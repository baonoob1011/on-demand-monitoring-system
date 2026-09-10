#!/usr/bin/env bash
set -euo pipefail

echo '========================================'
echo ' Cleaning previous drone simulation...'
echo '========================================'

pkill -9 -f '/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts/wsl-control.sh' || true
pkill -9 -f '/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts/wsl-telemetry.sh' || true
pkill -9 -f '/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts/wsl-camera-view.sh' || true
pkill -9 -f '/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts/wsl-sensor-monitor.sh' || true
pkill -9 -f 'python(3)? flight_controller.py' || true
pkill -9 -f 'python(3)? telemetry_sender.py' || true
pkill -9 -f 'python(3)? downward_camera_viewer.py' || true
pkill -9 -x px4 || true
pkill -9 -x gz || true
pkill -9 -x ruby || true
pkill -9 -u "$USER" -x mavsdk_server 2>/dev/null || true
rm -f /tmp/forest3d_sitl_battery_state.json /tmp/forest3d_sitl_battery_state.tmp || true

sleep 2

if pgrep -u "$USER" -x 'px4|gz|ruby' >/dev/null 2>&1 || pgrep -u "$USER" -f 'flight_controller.py|telemetry_sender.py|downward_camera_viewer.py|wsl-control.sh|wsl-telemetry.sh|wsl-camera-view.sh|wsl-sensor-monitor.sh' >/dev/null 2>&1; then
    echo '[WARN] Some PX4/Gazebo/Ruby processes are still running:'
    pgrep -a -u "$USER" -x 'px4|gz|ruby' || true
    pgrep -a -u "$USER" -f 'flight_controller.py|telemetry_sender.py|downward_camera_viewer.py|wsl-control.sh|wsl-telemetry.sh|wsl-camera-view.sh|wsl-sensor-monitor.sh' || true
else
    echo '[OK] Old PX4/Gazebo/Ruby processes stopped.'
fi
