#!/usr/bin/env bash
set -uo pipefail

echo 'Waiting 35s for PX4 + Gazebo to fully initialize...'
sleep 35

REPO_CONTROLLER="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone-controller"
cd ~/drone-controller || exit 1
cp "$REPO_CONTROLLER/flight_controller.py" flight_controller.py

source ~/drone-env/bin/activate

MAVSDK_BIN="$HOME/drone-env/lib/python3.12/site-packages/mavsdk/bin/mavsdk_server"
MAVSDK_LOG="$PWD/mavsdk_control.log"
MAVSDK_PORT=50052
MAVSDK_PID=""
MONITOR_PID=""
RESTART_COUNT=0
MAX_RESTARTS=10

is_port_listening() {
    ss -ltn 2>/dev/null | grep -q ":${MAVSDK_PORT} "
}

print_server_log_tail() {
    printf '%s\n' '[MAVSDK] Last server log:'
    tail -n 8 "$MAVSDK_LOG" 2>/dev/null || true
}

kill_stale_mavsdk_server() {
    pkill -f "mavsdk_server.*${MAVSDK_PORT}" 2>/dev/null || true

    for _ in $(seq 1 10); do
        if ! is_port_listening; then
            return 0
        fi
        sleep 0.3
    done

    printf '[MAVSDK] Port %s still busy after stale cleanup\n' "$MAVSDK_PORT"
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
}

wait_for_mavsdk_port() {
    for _ in $(seq 1 20); do
        if [ -n "${MAVSDK_PID:-}" ] && ! kill -0 "$MAVSDK_PID" 2>/dev/null; then
            printf '%s\n' '[MAVSDK] Server exited unexpectedly'
            print_server_log_tail
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
            printf '%s\n' '[MAVSDK] Server exited unexpectedly'
            print_server_log_tail
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

        printf '%s\n' '[MAVSDK] Server exited unexpectedly'
        print_server_log_tail

        if [ "$RESTART_COUNT" -ge "$MAX_RESTARTS" ]; then
            printf '%s\n' '[MAVSDK] Restart limit reached'
            return 0
        fi

        RESTART_COUNT=$((RESTART_COUNT + 1))
        sleep_seconds=$((2 + RESTART_COUNT * 2))
        if [ "$sleep_seconds" -gt 10 ]; then
            sleep_seconds=10
        fi

        printf '[MAVSDK] Restart attempt %s/%s in %ss\n' "$RESTART_COUNT" "$MAX_RESTARTS" "$sleep_seconds"
        sleep "$sleep_seconds"
        kill_stale_mavsdk_server || true
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
