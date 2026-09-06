#!/usr/bin/env bash
set -euo pipefail

echo 'Waiting 35s for PX4 + Gazebo to fully initialize...'
sleep 35

REPO_CONTROLLER="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone-controller"
cd ~/drone-controller
cp "$REPO_CONTROLLER/flight_controller.py" flight_controller.py

pkill -9 -u "$USER" -f 'mavsdk_server.*50052' 2>/dev/null || true

source ~/drone-env/bin/activate
MAVSDK_BIN="$HOME/drone-env/lib/python3.12/site-packages/mavsdk/bin/mavsdk_server"
MAVSDK_LOG="$PWD/mavsdk_control.log"
MAVSDK_PID=""
MONITOR_PID=""

start_mavsdk_server() {
    echo '[CMD] Starting mavsdk_server for control on port 50052...'
    "$MAVSDK_BIN" -p 50052 --sysid 245 --compid 191 udpin://0.0.0.0:14030 > "$MAVSDK_LOG" 2>&1 &
    MAVSDK_PID=$!
}

wait_for_mavsdk_port() {
    for _ in $(seq 1 20); do
        if ! kill -0 "$MAVSDK_PID" 2>/dev/null; then
            echo '[ERR] mavsdk_server exited before opening port 50052.'
            echo '[ERR] Last mavsdk_control.log lines:'
            tail -40 "$MAVSDK_LOG" || true
            return 1
        fi

        if ss -ltn 2>/dev/null | grep -q ':50052 '; then
            echo '[CMD] mavsdk_server is listening on port 50052.'
            return 0
        fi

        sleep 1
    done

    echo '[ERR] mavsdk_server did not open port 50052 in time.'
    echo '[ERR] Last mavsdk_control.log lines:'
    tail -40 "$MAVSDK_LOG" || true
    return 1
}

monitor_mavsdk_server() {
    while true; do
        sleep 2
        if [ -n "${MAVSDK_PID:-}" ] && kill -0 "$MAVSDK_PID" 2>/dev/null; then
            continue
        fi

        echo '[WARN] mavsdk_server stopped; restarting control bridge...'
        start_mavsdk_server
        wait_for_mavsdk_port || true
    done
}

cleanup() {
    echo '[CMD] Cleaning up flight-control mavsdk_server...'
    [ -n "${MONITOR_PID:-}" ] && kill "$MONITOR_PID" 2>/dev/null || true
    [ -n "${MAVSDK_PID:-}" ] && kill "$MAVSDK_PID" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

start_mavsdk_server
wait_for_mavsdk_port
monitor_mavsdk_server &
MONITOR_PID=$!

python flight_controller.py
