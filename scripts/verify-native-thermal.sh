#!/usr/bin/env bash
set -euo pipefail

WORLD_FILE="${1:-/usr/share/gz/gz-sim8/worlds/thermal_camera.sdf}"
TOPIC="${2:-/thermal_camera}"
LOG_FILE="${THERMAL_VERIFY_LOG:-/tmp/omss-native-thermal.log}"
PROJECT_PATH="${PROJECT_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SIM_PID=""
export GZ_PARTITION="${GZ_PARTITION:-omss_thermal_verify_$$}"

cleanup() {
    if [[ -n "$SIM_PID" ]]; then
        kill -- "-$SIM_PID" 2>/dev/null || kill "$SIM_PID" 2>/dev/null || true
        wait "$SIM_PID" 2>/dev/null || true
    fi
    pkill -f "gz sim -r -s $WORLD_FILE" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

setsid gz sim -r -s "$WORLD_FILE" >"$LOG_FILE" 2>&1 &
SIM_PID=$!

topic_exists() {
    timeout 1 gz topic -l 2>/dev/null | grep -Fxq "$TOPIC"
}

for _ in $(seq 1 12); do
    if topic_exists; then
        break
    fi
    sleep 0.5
done

if ! topic_exists; then
    echo "Thermal topic not found: $TOPIC" >&2
    tail -n 40 "$LOG_FILE" >&2 || true
    exit 1
fi

echo "Thermal topic: $TOPIC"
timeout 2 gz topic -i -t "$TOPIC" || true
echo "First frame:"
PYTHONPATH="/usr/lib/python3/dist-packages:${PYTHONPATH:-}" \
    timeout 7 python3 "$PROJECT_PATH/tools/inspect_thermal_topic.py" "$TOPIC" --timeout 5
