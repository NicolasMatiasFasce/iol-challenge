#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
REPORT_FILE="$RUN_DIR/test-report.txt"
RULES_FILE="$ROOT_DIR/iol-challenge/rate-limiter-rules.yaml"
RULES_BACKUP_FILE="$RUN_DIR/rate-limiter-rules.yaml.bak"
PYTHON3_BIN=""
LOAD_PROBE_LIMITED429=0
SMOKE_HTTP_CODE=""
LOAD_PROBE_NON429=0
ENDPOINT_TOTAL_LIMITED429=0
IDENTITY_TOTAL_LIMITED429=0
XFF_HOT_LIMITED429=0

timestamp() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

log_step() {
  echo "[$(timestamp)] [STEP] $1"
}

log_info() {
  echo "[$(timestamp)] [INFO] $1"
}

append_report() {
  echo "$1" >>"$REPORT_FILE"
}

print_console_summary() {
  echo
  echo "================ Test Summary ================"
  echo "Smoke:                        http_code=${SMOKE_HTTP_CODE}"
  echo "Load probe (500):             non429=${LOAD_PROBE_NON429} limited429=${LOAD_PROBE_LIMITED429}"
  echo "Endpoint isolation (500):     total_limited429=${ENDPOINT_TOTAL_LIMITED429} (vs load=${LOAD_PROBE_LIMITED429})"
  echo "Identity isolation (500):     total_limited429=${IDENTITY_TOTAL_LIMITED429} (vs load=${LOAD_PROBE_LIMITED429})"
  echo "X-Forwarded-For isolation:    hot_limited429=${XFF_HOT_LIMITED429}"
  echo "Result:                       PASS"
  echo "Detailed report:              ${REPORT_FILE}"
  echo "=============================================="
}

request_code() {
  local method="$1"
  local path="$2"
  local header_a="${3:-}"
  local header_b="${4:-}"

  if [[ -n "$header_a" && -n "$header_b" ]]; then
    curl -s -o /dev/null -w "%{http_code}" -X "$method" "http://127.0.0.1:8080$path" -H "$header_a" -H "$header_b"
    return
  fi

  if [[ -n "$header_a" ]]; then
    curl -s -o /dev/null -w "%{http_code}" -X "$method" "http://127.0.0.1:8080$path" -H "$header_a"
    return
  fi

  curl -s -o /dev/null -w "%{http_code}" -X "$method" "http://127.0.0.1:8080$path"
}

generate_codes_file() {
  local method="$1"
  local path="$2"
  local total="$3"
  local concurrency="$4"
  local header_a="${5:-}"
  local header_b="${6:-}"
  local codes_file
  codes_file="$(mktemp)"

  for i in $(seq 1 "$total"); do
    {
      request_code "$method" "$path" "$header_a" "$header_b"
      echo
    } >>"$codes_file" &

    if (( i % concurrency == 0 )); then
      wait
    fi
  done
  wait

  echo "$codes_file"
}

count_code() {
  local codes_file="$1"
  local code="$2"
  grep -c "^${code}$" "$codes_file" || true
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required command not found: $1" >&2
    exit 1
  fi
}

resolve_cmd_path() {
  local cmd="$1"
  local resolved
  resolved="$(type -P "$cmd" || true)"
  if [[ -z "$resolved" ]]; then
    echo "Error: required executable not found in PATH: $cmd" >&2
    exit 1
  fi
  echo "$resolved"
}

is_port_open() {
  local host="$1"
  local port="$2"
  (echo >"/dev/tcp/$host/$port") >/dev/null 2>&1
}

cleanup() {
  restore_rules_if_needed
  log_info "Cleaning local environment (app, upstream, redis container)"
  "$ROOT_DIR/scripts/dev-down.sh" >/dev/null 2>&1 || true
}

configure_strict_rules_for_script() {
  mkdir -p "$RUN_DIR"
  cp "$RULES_FILE" "$RULES_BACKUP_FILE"

  cat >"$RULES_FILE" <<'YAML'
rules:
  - name: script-strict-default
    method: "*"
    route: "*"
    upstreamUrl: "http://localhost:8081"
    capacity: 20
    refillRatePerSecond: 5
    identityHeader: X-Api-Key
    failMode: fail-open
YAML

  append_report "rules_mode=strict-temporary"
  log_info "Temporary strict rules enabled for script evidence (capacity=20, refill=5)"
}

restore_rules_if_needed() {
  if [[ -f "$RULES_BACKUP_FILE" ]]; then
    cp "$RULES_BACKUP_FILE" "$RULES_FILE"
    rm -f "$RULES_BACKUP_FILE"
    log_info "Original rules restored"
  fi
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

  log_step "Starting dummy upstream on 127.0.0.1:8081"

  if is_port_open "127.0.0.1" "8081"; then
    log_info "Upstream already reachable on 127.0.0.1:8081"
    return 0
  fi

  mkdir -p "$RUN_DIR"
  (
    cd "$ROOT_DIR"
    nohup "$PYTHON3_BIN" -m http.server 8081 >"$RUN_DIR/upstream.log" 2>&1 &
    echo $! >"$upstream_pid_file"
  )

  if ! wait_for_http "http://127.0.0.1:8081" 30; then
    echo "Dummy upstream failed to start" >&2
    exit 1
  fi

  log_info "Dummy upstream ready on http://127.0.0.1:8081"
}

run_forwarding_smoke_check() {
  local smoke_identity="smoke-$(date +%s)-$$"
  local code

  log_step "Smoke check: verifying requests are forwarded (not blocked by limiter)"
  code="$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:8080/rl/users/123" -H "X-Api-Key: $smoke_identity")"

  append_report "Smoke forwarding"
  append_report "identity=$smoke_identity"
  append_report "http_code=$code"
  SMOKE_HTTP_CODE="$code"

  if [[ "$code" == "000" ]]; then
    echo "Smoke forwarding failed: app or upstream unreachable (HTTP 000)." >&2
    return 1
  fi

  if [[ "$code" == "429" ]]; then
    echo "Smoke forwarding failed: first request was rate-limited unexpectedly." >&2
    return 1
  fi

  log_info "Smoke forwarding OK (http_code=$code)"
}

run_http_probe() {
  local total=500
  local concurrency=120
  local minimum_limited=80
  local ok=0
  local limited=0
  local probe_identity="probe-$(date +%s)-$$"
  local codes_file
  codes_file="$(generate_codes_file "GET" "/rl/users/123" "$total" "$concurrency" "X-Api-Key: $probe_identity")"

  log_step "Load probe: stressing limiter to observe throttle behavior (429)"

  while IFS= read -r code; do
    if [[ "$code" == "429" ]]; then
      limited=$((limited + 1))
    elif [[ -n "$code" ]]; then
      ok=$((ok + 1))
    fi
  done <"$codes_file"

  rm -f "$codes_file"

  append_report ""
  append_report "HTTP load probe"
  append_report "total=$total"
  append_report "identity=$probe_identity"
  append_report "non429=$ok"
  append_report "limited429=$limited"
  append_report "minimum_limited429_expected=$minimum_limited"

  log_info "Load probe completed (total=$total, non429=$ok, limited429=$limited)"
  LOAD_PROBE_LIMITED429="$limited"
  LOAD_PROBE_NON429="$ok"

  if [[ "$limited" -lt "$minimum_limited" ]]; then
    echo "HTTP probe failed: expected at least $minimum_limited responses 429, got $limited." >&2
    append_report "error=insufficient_429_in_load_probe"
    return 1
  fi
}

run_endpoint_isolation_probe() {
  local identity="endpoint-iso-$(date +%s)-$$"
  local hot_path="/rl/users/123"
  local control_path="/rl/orders/321"
  local hot_codes_file
  local control_codes_file
  local hot_limited
  local control_limited
  local total_requests=500
  local half_requests=250
  local total_limited

  log_step "Isolation probe: endpoint A load should not affect endpoint B"

  hot_codes_file="$(generate_codes_file "GET" "$hot_path" "$half_requests" 120 "X-Api-Key: $identity")"
  hot_limited="$(count_code "$hot_codes_file" "429")"
  rm -f "$hot_codes_file"

  control_codes_file="$(generate_codes_file "GET" "$control_path" "$half_requests" 120 "X-Api-Key: $identity")"
  control_limited="$(count_code "$control_codes_file" "429")"
  rm -f "$control_codes_file"
  total_limited=$((hot_limited + control_limited))

  append_report ""
  append_report "Endpoint isolation probe"
  append_report "identity=$identity"
  append_report "total_requests=$total_requests"
  append_report "hot_path=$hot_path"
  append_report "hot_path_limited429=$hot_limited"
  append_report "control_path=$control_path"
  append_report "control_path_limited429=$control_limited"
  append_report "total_limited429=$total_limited"
  append_report "load_probe_limited429_reference=$LOAD_PROBE_LIMITED429"

  log_info "Endpoint isolation completed (hot_limited429=$hot_limited, control_limited429=$control_limited, total_limited429=$total_limited)"
  ENDPOINT_TOTAL_LIMITED429="$total_limited"

  if [[ "$total_limited" -lt 1 ]]; then
    echo "Endpoint isolation failed: expected at least one 429 across 500 requests." >&2
    return 1
  fi

  if [[ "$total_limited" -ge "$LOAD_PROBE_LIMITED429" ]]; then
    echo "Endpoint isolation failed: expected fewer 429 than load probe (load=$LOAD_PROBE_LIMITED429, endpoint_isolation=$total_limited)." >&2
    return 1
  fi
}

run_identity_isolation_probe() {
  local hot_identity="identity-hot-$(date +%s)-$$"
  local control_identity="identity-control-$(date +%s)-$$"
  local path="/rl/users/123"
  local hot_codes_file
  local control_codes_file
  local hot_limited
  local control_limited
  local total_requests=500
  local half_requests=250
  local total_limited

  log_step "Isolation probe: identity A load should not affect identity B"

  hot_codes_file="$(generate_codes_file "GET" "$path" "$half_requests" 120 "X-Api-Key: $hot_identity")"
  hot_limited="$(count_code "$hot_codes_file" "429")"
  rm -f "$hot_codes_file"

  control_codes_file="$(generate_codes_file "GET" "$path" "$half_requests" 120 "X-Api-Key: $control_identity")"
  control_limited="$(count_code "$control_codes_file" "429")"
  rm -f "$control_codes_file"
  total_limited=$((hot_limited + control_limited))

  append_report ""
  append_report "Identity isolation probe"
  append_report "total_requests=$total_requests"
  append_report "path=$path"
  append_report "hot_identity=$hot_identity"
  append_report "hot_identity_limited429=$hot_limited"
  append_report "control_identity=$control_identity"
  append_report "control_identity_limited429=$control_limited"
  append_report "total_limited429=$total_limited"
  append_report "load_probe_limited429_reference=$LOAD_PROBE_LIMITED429"

  log_info "Identity isolation completed (hot_limited429=$hot_limited, control_limited429=$control_limited, total_limited429=$total_limited)"
  IDENTITY_TOTAL_LIMITED429="$total_limited"

  if [[ "$total_limited" -lt 1 ]]; then
    echo "Identity isolation failed: expected at least one 429 across 500 requests." >&2
    return 1
  fi

  if [[ "$total_limited" -ge "$LOAD_PROBE_LIMITED429" ]]; then
    echo "Identity isolation failed: expected fewer 429 than load probe (load=$LOAD_PROBE_LIMITED429, identity_isolation=$total_limited)." >&2
    return 1
  fi
}

run_x_forwarded_for_isolation_probe() {
  local hot_ip="201.10.10.1"
  local control_ip="201.10.10.2"
  local path="/rl/users/123"
  local hot_codes_file
  local control_codes_file
  local hot_limited
  local control_limited

  log_step "Isolation probe: X-Forwarded-For identity fallback should isolate clients"

  hot_codes_file="$(generate_codes_file "GET" "$path" 700 120 "X-Forwarded-For: $hot_ip")"
  hot_limited="$(count_code "$hot_codes_file" "429")"
  rm -f "$hot_codes_file"

  control_codes_file="$(generate_codes_file "GET" "$path" 10 5 "X-Forwarded-For: $control_ip")"
  control_limited="$(count_code "$control_codes_file" "429")"
  rm -f "$control_codes_file"

  append_report ""
  append_report "X-Forwarded-For isolation probe"
  append_report "path=$path"
  append_report "hot_ip=$hot_ip"
  append_report "hot_ip_limited429=$hot_limited"
  append_report "control_ip=$control_ip"
  append_report "control_ip_limited429=$control_limited"

  log_info "X-Forwarded-For isolation completed (hot_limited429=$hot_limited, control_limited429=$control_limited)"
  XFF_HOT_LIMITED429="$hot_limited"

  if [[ "$hot_limited" -lt 1 ]]; then
    append_report "warning=xff_isolation_hot_ip_no_429"
  fi

  if [[ "$control_limited" -gt 0 ]]; then
    echo "X-Forwarded-For isolation failed: control IP returned 429 unexpectedly." >&2
    return 1
  fi
}

main() {
  require_cmd bash
  require_cmd curl
  require_cmd python3

  PYTHON3_BIN="$(resolve_cmd_path python3)"

  trap cleanup EXIT

  mkdir -p "$RUN_DIR"
  : >"$REPORT_FILE"
  append_report "Integration run"
  append_report "started_at=$(timestamp)"
  configure_strict_rules_for_script

  start_dummy_upstream

  log_step "Starting rate limiter app in background"
  "$ROOT_DIR/scripts/dev-up.sh" --background


  if ! wait_for_http "http://127.0.0.1:8080" 90; then
    echo "App not reachable on 8080" >&2
    exit 1
  fi

  log_info "App ready on http://127.0.0.1:8080"

  run_forwarding_smoke_check

  run_http_probe
  run_endpoint_isolation_probe
  run_identity_isolation_probe
  run_x_forwarded_for_isolation_probe

  append_report ""
  append_report "Script tests: PASS"
  append_report "completed_at=$(timestamp)"

  log_info "Test report generated at $REPORT_FILE"
  print_console_summary
}

main "$@"

