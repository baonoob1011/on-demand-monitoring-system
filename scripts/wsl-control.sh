#!/usr/bin/env bash
set -uo pipefail

echo 'Waiting 35s for PX4 + Gazebo to fully initialize...'
sleep 35

REPO_CONTROLLER="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone"
ENV_FILE="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/ondemandmonitoring/.env"
cd ~/drone-controller || exit 1
cp "$REPO_CONTROLLER/flight_controller.py" flight_controller.py

if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

source ~/drone-env/bin/activate

MAVSDK_BIN="$HOME/drone-env/lib/python3.12/site-packages/mavsdk/bin/mavsdk_server"
MAVSDK_LOG="$PWD/mavsdk_control.log"
MAVSDK_PORT=50052
MAVSDK_PID=""
MONITOR_PID=""
RESTART_COUNT=0
MAX_RESTARTS=10
SERVER_RESTART_COOLDOWN_S="${MAVSDK_SERVER_RESTART_COOLDOWN_S:-5}"

is_port_listening() {
    ss -ltn 2>/dev/null | grep -q ":${MAVSDK_PORT} "
}

print_server_log_tail() {
    printf '%s\n' '[MAVSDK] Last server log:'
    tail -n 8 "$MAVSDK_LOG" 2>/dev/null || true
}

server_exit_code() {
    local pid="$1"
    local code
    if wait "$pid" 2>/dev/null; then
        code=0
    else
        code=$?
    fi
    printf '%s' "$code"
}

print_server_exit() {
    local reason="$1"
    local pid="${MAVSDK_PID:-}"
    local code="unknown"
    if [ -n "$pid" ]; then
        code="$(server_exit_code "$pid")"
    fi
    printf '[MAVSDK-CONN] Server process exited reason=%s pid=%s code=%s\n' "$reason" "${pid:-unknown}" "$code"
    print_server_log_tail
}

kill_stale_mavsdk_server() {
    printf '%s\n' '[MAVSDK] Cleaning stale mavsdk_server processes...'

    # Chỉ dọn MAVSDK control server trên port 50052.
    # Telemetry dùng chung gRPC server này nên không chạy server riêng.
    pkill -f "mavsdk_server.*-p ${MAVSDK_PORT}" 2>/dev/null || true

    for _ in $(seq 1 20); do
        grpc_busy=0
        udp_busy=0

        ss -ltn 2>/dev/null | grep -q ":${MAVSDK_PORT} " && grpc_busy=1
        ss -lun 2>/dev/null | grep -q ":14030 " && udp_busy=1

        if [ "$grpc_busy" -eq 0 ] && [ "$udp_busy" -eq 0 ]; then
            printf '%s\n' '[MAVSDK] Stale cleanup complete'
            return 0
        fi

        sleep 0.25
    done

    printf '%s\n' '[MAVSDK] WARNING: control ports still busy after cleanup'
    ss -ltnp 2>/dev/null | grep ":${MAVSDK_PORT} " || true
    ss -lunp 2>/dev/null | grep ":14030 " || true

    return 1
}

start_mavsdk_server() {
    printf '%s\n' '[MAVSDK] Starting server...'
    "$MAVSDK_BIN" \
        -p "$MAVSDK_PORT" \
        --sysid 245 \
        --compid 191 \
        udpin://0.0.0.0:14030 \
        > "$MAVSDK_LOG" 2>&1 &
    MAVSDK_PID=$!
    printf '[MAVSDK] Started pid=%s port=%s\n' "$MAVSDK_PID" "$MAVSDK_PORT"
}

wait_for_mavsdk_port() {
    for _ in $(seq 1 20); do
        if [ -n "${MAVSDK_PID:-}" ] && ! kill -0 "$MAVSDK_PID" 2>/dev/null; then
            print_server_exit "startup-port-wait"
            return 1
        fi

        if is_port_listening; then
            printf '[MAVSDK] Listening on %s\n' "$MAVSDK_PORT"
            return 0
        fi

        sleep 1
    done

    printf '[MAVSDK] Server did not open port %s in time\n' "$MAVSDK_PORT"
    print_server_log_tail
    return 1
}

wait_for_mavsdk_system() {
    for _ in $(seq 1 30); do
        if [ -n "${MAVSDK_PID:-}" ] && ! kill -0 "$MAVSDK_PID" 2>/dev/null; then
            print_server_exit "px4-discovery-wait"
            return 1
        fi

        if grep -q 'System discovered' "$MAVSDK_LOG" 2>/dev/null; then
            printf '%s\n' '[MAVSDK] PX4 discovered'
            sleep 2
            return 0
        fi

        sleep 1
    done

    printf '%s\n' '[MAVSDK] PX4 discovery timeout; continuing in degraded mode'
    print_server_log_tail
    return 1
}

start_and_wait_mavsdk() {
    start_mavsdk_server
    wait_for_mavsdk_port || return 1
    wait_for_mavsdk_system || true
    return 0
}

monitor_mavsdk_server() {
    while true; do
        sleep 2

        if [ -n "${MAVSDK_PID:-}" ] && kill -0 "$MAVSDK_PID" 2>/dev/null; then
            continue
        fi

        print_server_exit "monitor"

        if [ "$RESTART_COUNT" -ge "$MAX_RESTARTS" ]; then
            printf '%s\n' '[MAVSDK] Restart limit reached'
            return 0
        fi

        RESTART_COUNT=$((RESTART_COUNT + 1))
        sleep_seconds=$((SERVER_RESTART_COOLDOWN_S + RESTART_COUNT * 2))
        if [ "$sleep_seconds" -gt 10 ]; then
            sleep_seconds=10
        fi

        printf '[MAVSDK] Restart attempt %s/%s in %ss\n' "$RESTART_COUNT" "$MAX_RESTARTS" "$sleep_seconds"
        sleep "$sleep_seconds"
        start_and_wait_mavsdk || true
    done
}

cleanup() {
    printf '%s\n' '[CMD] Cleaning up flight-control mavsdk_server...'
    [ -n "${MONITOR_PID:-}" ] && kill "$MONITOR_PID" 2>/dev/null || true
    [ -n "${MAVSDK_PID:-}" ] && kill "$MAVSDK_PID" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

kill_stale_mavsdk_server || true
start_and_wait_mavsdk || true
monitor_mavsdk_server &
MONITOR_PID=$!

python flight_controller.py
