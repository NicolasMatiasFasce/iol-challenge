#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/iol-challenge"
RUN_DIR="$ROOT_DIR/.run"
REPORT_FILE="$RUN_DIR/test-report.txt"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required command not found: $1" >&2
    exit 1
  fi
}

is_port_open() {
  local host="$1"
  local port="$2"
  (echo >"/dev/tcp/$host/$port") >/dev/null 2>&1
}

cleanup() {
  "$ROOT_DIR/scripts/dev-down.sh" >/dev/null 2>&1 || true
}

wait_for_http() {
  local url="$1"
  local retries="${2:-60}"

  for _ in $(seq 1 "$retries"); do
    if curl --silent --max-time 1 "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  return 1
}

start_dummy_upstream() {
  local upstream_pid_file="$RUN_DIR/upstream.pid"

  if is_port_open "127.0.0.1" "8081"; then
    echo "Upstream already reachable on 127.0.0.1:8081"
    return 0
  fi

  mkdir -p "$RUN_DIR"
  (
    cd "$ROOT_DIR"
    nohup python3 -m http.server 8081 >"$RUN_DIR/upstream.log" 2>&1 &
    echo $! >"$upstream_pid_file"
  )

  if ! wait_for_http "http://127.0.0.1:8081" 30; then
    echo "Dummy upstream failed to start" >&2
    exit 1
  fi
}

run_http_probe() {
  local total=30
  local ok=0
  local limited=0

  for _ in $(seq 1 "$total"); do
    code="$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:8080/rl/users/123" -H "X-Api-Key: demo-client")"
    if [[ "$code" == "429" ]]; then
      limited=$((limited + 1))
    elif [[ "$code" =~ ^2|3|4|5 ]]; then
      ok=$((ok + 1))
    fi
  done

  {
    echo "HTTP probe summary"
    echo "total=$total"
    echo "non429=$ok"
    echo "limited429=$limited"
  } >>"$REPORT_FILE"
}

main() {
  require_cmd bash
  require_cmd curl
  require_cmd mvn
  require_cmd python3

  trap cleanup EXIT

  mkdir -p "$RUN_DIR"
  : >"$REPORT_FILE"

  start_dummy_upstream
  "$ROOT_DIR/scripts/dev-up.sh" --background


  if ! wait_for_http "http://127.0.0.1:8080" 90; then
    echo "App not reachable on 8080" >&2
    exit 1
  fi

  run_http_probe

  (
    cd "$APP_DIR"
    mvn -q test
  )

  {
    echo
    echo "Maven tests: PASS"
    echo "Completed at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  } >>"$REPORT_FILE"

  echo "Test report generated at $REPORT_FILE"
  cat "$REPORT_FILE"
}

main "$@"

