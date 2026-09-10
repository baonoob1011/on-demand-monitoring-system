#!/usr/bin/env bash
set -euo pipefail

PROJECT_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system"
FOREST3D_PATH="$PROJECT_PATH/Forest3D"
PX4_ROOT="$HOME/PX4-Autopilot"
SIM_WORLD="${SIM_WORLD:-compact}"

if [ -f "$PROJECT_PATH/drone/.env.example" ]; then
    set -a
    # shellcheck disable=SC1091
    source "$PROJECT_PATH/drone/.env.example"
    set +a
fi

case "$SIM_WORLD" in
    compact)
        WORLD_NAME="forest_monitoring_compact"
        MODEL_PREFIX="x500_mono_cam_down"
        ;;
    legacy)
        WORLD_NAME="forest_monitoring"
        MODEL_PREFIX="x500"
        ;;
    *)
        WORLD_NAME="${GZ_WORLD_NAME:-forest_monitoring_compact}"
        MODEL_PREFIX="${GZ_MODEL_PREFIX:-x500}"
        ;;
esac

export GZ_SIM_RESOURCE_PATH="${FOREST3D_PATH}:${FOREST3D_PATH}/models:$PX4_ROOT/Tools/simulation/gz/models:$PX4_ROOT/Tools/simulation/gz/worlds:${GZ_SIM_RESOURCE_PATH:-}"
export PYTHONPATH="$PROJECT_PATH:${PYTHONPATH:-}"
export SENSOR_VIS_ENABLED="${SENSOR_VIS_ENABLED:-true}"
export SENSOR_VIS_LIDAR_2D_ENABLED="${SENSOR_VIS_LIDAR_2D_ENABLED:-true}"
export SENSOR_VIS_LIDAR_3D_ENABLED="${SENSOR_VIS_LIDAR_3D_ENABLED:-true}"
export LIDAR_TOPIC="${LIDAR_TOPIC:-/lidar}"
export POINTCLOUD_LIDAR_TOPIC="${POINTCLOUD_LIDAR_TOPIC:-/lidar_3d}"
export GAZEBO_CAMERA_TOPIC="${GAZEBO_CAMERA_TOPIC:-/world/${WORLD_NAME}/model/${MODEL_PREFIX}_0/link/camera_link/sensor/camera/image}"

echo "========================================"
echo " Forest3D Sensor Visual Dashboard"
echo "========================================"
echo "World : $WORLD_NAME"
echo "Drone : ${MODEL_PREFIX}_0"
echo "Camera: $GAZEBO_CAMERA_TOPIC"
echo "2D    : $LIDAR_TOPIC"
echo "3D    : $POINTCLOUD_LIDAR_TOPIC"
echo
echo "This opens the LiDAR 2D and LiDAR 3D realtime windows."
echo "The Downward Camera window is opened by wsl-camera-view.sh."
echo

cd "$PROJECT_PATH"
source "$HOME/drone-env/bin/activate"
exec python3 -m drone.visualization.sensor_dashboard
