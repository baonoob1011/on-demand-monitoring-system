#!/usr/bin/env bash
set -e

sleep 22
REPO_CONTROLLER="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone-controller"
cd ~/drone-controller
cp "$REPO_CONTROLLER/telemetry_sender.py" telemetry_sender.py

pkill -9 -u "$USER" -f 'mavsdk_server.*50051' 2>/dev/null || true

source ~/drone-env/bin/activate
python telemetry_sender.py
