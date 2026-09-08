#!/usr/bin/env bash
set -e

PROJECT_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system"
FOREST3D_PATH="$PROJECT_PATH/Forest3D"
DRONE_PATH="$PROJECT_PATH/drone"
ENV_FILE="$PROJECT_PATH/ondemandmonitoring/.env"

if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

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

source ~/drone-env/bin/activate

for _ in $(seq 1 60); do

    if gz topic -l 2>/dev/null | grep -Fxq "$CAMERA_TOPIC"; then

        echo "[CAMERA] Topic found."
        echo "[CAMERA] Opening HUD viewer:"
        echo "         $CAMERA_TOPIC"

        sleep 1

        cd "$DRONE_PATH"
        exec python3 downward_camera_viewer.py
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
