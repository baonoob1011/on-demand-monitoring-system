#!/usr/bin/env bash
set -e

echo 'Waiting 35s for PX4 + Gazebo to fully initialize...'
sleep 35

REPO_CONTROLLER="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone-controller"
cd ~/drone-controller
cp "$REPO_CONTROLLER/flight_controller.py" flight_controller.py

pkill -9 -u "$USER" -f 'mavsdk_server.*50052' 2>/dev/null || true

source ~/drone-env/bin/activate

# Explicitly start mavsdk_server in the background
echo '[CMD] Starting mavsdk_server for control on port 50052...'
~/drone-env/lib/python3.12/site-packages/mavsdk/bin/mavsdk_server -p 50052 --sysid 245 --compid 191 udpin://0.0.0.0:14030 > mavsdk_control.log 2>&1 &
MAVSDK_PID=$!
sleep 2

# Cleanup trap to ensure mavsdk_server dies when this script exits
trap "echo '[CMD] Cleaning up mavsdk_server (PID: $MAVSDK_PID)...'; kill $MAVSDK_PID 2>/dev/null || true" EXIT

python flight_controller.py
