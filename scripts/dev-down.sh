#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
APP_PID_FILE="$RUN_DIR/app.pid"
UPSTREAM_PID_FILE="$RUN_DIR/upstream.pid"

MODE="stop"
if [[ "${1:-}" == "--purge" ]]; then
  MODE="purge"
fi

stop_pid_if_running() {
  local pid_file="$1"
  local label="$2"

  if [[ ! -f "$pid_file" ]]; then
    echo "$label pid file not found (nothing to stop)"
    return 0
  fi

  local pid
  pid="$(cat "$pid_file" || true)"
  if [[ -z "$pid" ]]; then
    rm -f "$pid_file"
    echo "$label pid file was empty and was cleaned"
    return 0
  fi

  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    # Give process a brief grace period.
    sleep 1
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
    echo "$label stopped (pid $pid)"
  else
    echo "$label was not running (stale pid $pid)"
  fi

  rm -f "$pid_file"
}

stop_app_fallback() {
  # Fallback idempotente para procesos de la app si app.pid no existe o quedo stale.
  local stopped_any="false"

  if pkill -f "mvn -q spring-boot:run" >/dev/null 2>&1; then
    stopped_any="true"
  fi

  if pkill -f "iolchallenge.Main" >/dev/null 2>&1; then
    stopped_any="true"
  fi

  if [[ "$stopped_any" == "true" ]]; then
    echo "App stopped by fallback process match"
  fi
}

stop_upstream_fallback() {
  # Fallback idempotente: intenta cerrar upstream dummy aunque falte upstream.pid.
  local stopped_any="false"

  if pkill -f "scripts/dummy_upstream.py" >/dev/null 2>&1; then
    stopped_any="true"
  fi

  if pkill -f "python3 -m http.server 8081" >/dev/null 2>&1; then
    stopped_any="true"
  fi

  if pkill -f "python -m http.server 8081" >/dev/null 2>&1; then
    stopped_any="true"
  fi

  if [[ "$stopped_any" == "true" ]]; then
    echo "Upstream stopped by fallback process match on port 8081"
  fi
}

stop_redis_container() {
  if docker ps --format '{{.Names}}' | grep -q '^rl-redis$'; then
    docker stop rl-redis >/dev/null
    echo "Redis container rl-redis stopped"
  else
    echo "Redis container rl-redis is not running"
  fi

  if [[ "$MODE" == "purge" ]]; then
    if docker ps -a --format '{{.Names}}' | grep -q '^rl-redis$'; then
      docker rm rl-redis >/dev/null
      echo "Redis container rl-redis removed"
    else
      echo "Redis container rl-redis does not exist"
    fi
  fi
}

main() {
  stop_pid_if_running "$APP_PID_FILE" "App"
  stop_app_fallback
  stop_pid_if_running "$UPSTREAM_PID_FILE" "Upstream"
  stop_upstream_fallback
  stop_redis_container
}

main "$@"

