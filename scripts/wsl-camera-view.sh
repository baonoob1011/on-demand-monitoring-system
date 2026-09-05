#!/usr/bin/env bash
set -e

FOREST3D_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/Forest3D"
CAMERA_TOPIC="${GAZEBO_CAMERA_TOPIC:-/world/forest_monitoring/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image}"
CAMERA_VIEW_CONFIG="$FOREST3D_PATH/gui/downward_camera_view.config"

echo 'Waiting for downward camera stream...'

for _ in $(seq 1 60); do
    if gz topic -l 2>/dev/null | grep -Fxq "$CAMERA_TOPIC"; then
        echo "Opening camera viewer: $CAMERA_TOPIC"

        sleep 2

        exec gz gui -c "$CAMERA_VIEW_CONFIG"
    fi

    sleep 1
done

echo "Camera topic was not found: $CAMERA_TOPIC"
echo 'Run this to inspect available camera topics:'
echo '  gz topic -l | grep camera'

exec bash