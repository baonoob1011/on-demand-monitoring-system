#!/usr/bin/env bash
set -e

echo 'Waiting 35s for PX4 + Gazebo to fully initialize...'
sleep 35

REPO_CONTROLLER="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone-controller"
cd ~/drone-controller
cp "$REPO_CONTROLLER/flight_controller.py" flight_controller.py

pkill -9 -u "$USER" -f 'mavsdk_server.*50052' 2>/dev/null || true

source ~/drone-env/bin/activate
python flight_controller.py
