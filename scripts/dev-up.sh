#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT_DIR/iol-challenge"
RUN_DIR="$ROOT_DIR/.run"
APP_LOG="$RUN_DIR/app.log"
APP_PID_FILE="$RUN_DIR/app.pid"

MODE="foreground"
if [[ "${1:-}" == "--background" ]]; then
  MODE="background"
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required command not found: $1" >&2
    exit 1
  fi
}

wait_for_http() {
  local url="$1"
  local retries="${2:-60}"
  local sleep_seconds="${3:-1}"

  for _ in $(seq 1 "$retries"); do
    if curl --silent --max-time 1 "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$sleep_seconds"
  done

  return 1
}

start_redis() {
  if ! docker ps --format '{{.Names}}' | grep -q '^rl-redis$'; then
    if docker ps -a --format '{{.Names}}' | grep -q '^rl-redis$'; then
      docker start rl-redis >/dev/null
    else
      docker run --name rl-redis -p 6379:6379 -d redis:7 >/dev/null
    fi
  fi
}

start_app_background() {
  mkdir -p "$RUN_DIR"

  if [[ -f "$APP_PID_FILE" ]]; then
    local existing_pid
    existing_pid="$(cat "$APP_PID_FILE")"
    if kill -0 "$existing_pid" >/dev/null 2>&1; then
      echo "App already running with pid $existing_pid"
      return 0
    fi
  fi

  (
    cd "$APP_DIR"
    nohup mvn -q spring-boot:run >"$APP_LOG" 2>&1 &
    echo $! >"$APP_PID_FILE"
  )

  if wait_for_http "http://127.0.0.1:8080" 90 1; then
    echo "App ready on http://127.0.0.1:8080"
  else
    echo "App did not become ready. Check $APP_LOG" >&2
    exit 1
  fi
}

start_app_foreground() {
  cd "$APP_DIR"
  exec mvn spring-boot:run
}

main() {
  require_cmd docker
  require_cmd mvn
  require_cmd curl

  start_redis

  if [[ "$MODE" == "background" ]]; then
    start_app_background
  else
    start_app_foreground
  fi
}

main "$@"

