#!/usr/bin/env bash
set -euo pipefail

sleep 22

REPO_CONTROLLER="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone"
ENV_FILE="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/ondemandmonitoring/.env"
cd ~/drone-controller
cp "$REPO_CONTROLLER/telemetry_sender.py" telemetry_sender.py
cp "$REPO_CONTROLLER/sitl_battery_sim.py" sitl_battery_sim.py

if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

source ~/drone-env/bin/activate

MAVSDK_PORT="${MAVSDK_TELEMETRY_GRPC_PORT:-50052}"

echo "[MAVSDK-TEL] Using shared MAVSDK gRPC server on ${MAVSDK_PORT}"

for _ in $(seq 1 60); do
    if ss -ltn 2>/dev/null | grep -q ":${MAVSDK_PORT} "; then
        echo "[MAVSDK-TEL] Shared server ready on ${MAVSDK_PORT}"
        exec python telemetry_sender.py
    fi

    sleep 1
done

echo "[MAVSDK-TEL] ERROR: shared MAVSDK server ${MAVSDK_PORT} not ready"
echo "[MAVSDK-TEL] Start/restart Flight Control; it owns mavsdk_server."
exit 1
