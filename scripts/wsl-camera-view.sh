#!/usr/bin/env bash
set -e

FOREST3D_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/Forest3D"

SIM_WORLD="${SIM_WORLD:-compact}"

if [ "$SIM_WORLD" = "compact" ]; then
    WORLD_NAME="forest_monitoring_compact"
else
    WORLD_NAME="${GZ_WORLD_NAME:-forest_monitoring}"
fi

MODEL_NAME="${GZ_MODEL_NAME:-x500_mono_cam_down_0}"

CAMERA_TOPIC="${GAZEBO_CAMERA_TOPIC:-/world/${WORLD_NAME}/model/${MODEL_NAME}/link/camera_link/sensor/camera/image}"

CAMERA_VIEW_CONFIG="$FOREST3D_PATH/gui/downward_camera_view.config"
RUNTIME_CONFIG="/tmp/downward_camera_view_${WORLD_NAME}.config"

echo "========================================"
echo " Downward Camera Viewer"
echo "========================================"
echo "World : $WORLD_NAME"
echo "Model : $MODEL_NAME"
echo "Topic : $CAMERA_TOPIC"
echo

echo "[CAMERA] Waiting for camera stream..."

for _ in $(seq 1 60); do

    if gz topic -l 2>/dev/null | grep -Fxq "$CAMERA_TOPIC"; then

        echo "[CAMERA] Topic found."
        echo "[CAMERA] Preparing viewer..."

        # Create runtime GUI config so the viewer always subscribes
        # to the currently selected Gazebo world.
        sed \
          -e "s|/world/forest_monitoring/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image|${CAMERA_TOPIC}|g" \
          -e "s|/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image|${CAMERA_TOPIC}|g" \
          "$CAMERA_VIEW_CONFIG" > "$RUNTIME_CONFIG"

        echo "[CAMERA] Opening viewer:"
        echo "         $CAMERA_TOPIC"

        sleep 1

        exec gz gui -c "$RUNTIME_CONFIG"
    fi

    sleep 1
done

echo
echo "[ERROR] Camera topic was not found:"
echo "$CAMERA_TOPIC"
echo
echo "Available camera topics:"
gz topic -l 2>/dev/null | grep -Ei "camera|image" || true

exec bash