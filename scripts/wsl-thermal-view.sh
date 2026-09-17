#!/usr/bin/env bash
set -euo pipefail

PROJECT_PATH="${PROJECT_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
FOREST3D_PATH="${FOREST3D_PATH:-$PROJECT_PATH/Forest3D}"
PX4_ROOT="$HOME/PX4-Autopilot"
SIM_WORLD="${SIM_WORLD:-compact}"

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
        MODEL_PREFIX="${GZ_MODEL_PREFIX:-x500_mono_cam_down}"
        ;;
esac

export GZ_SIM_RESOURCE_PATH="${FOREST3D_PATH}:${FOREST3D_PATH}/models:$PX4_ROOT/Tools/simulation/gz/models:$PX4_ROOT/Tools/simulation/gz/worlds:${GZ_SIM_RESOURCE_PATH:-}"
export PYTHONPATH="$PROJECT_PATH:${PYTHONPATH:-}"
export SENSOR_VIS_ENABLED=true
export SENSOR_VIS_LIDAR_2D_ENABLED=false
export SENSOR_VIS_LIDAR_3D_ENABLED=false
export SENSOR_VIS_THERMAL_ENABLED=true
export GAZEBO_THERMAL_CAMERA_TOPIC="${GAZEBO_THERMAL_CAMERA_TOPIC:-/thermal_camera}"
export QT_QPA_FONTDIR="${QT_QPA_FONTDIR:-/usr/share/fonts/truetype/dejavu}"
export QT_QPA_PLATFORMTHEME="${QT_QPA_PLATFORMTHEME:-}"

echo "========================================"
echo " Forest3D Thermal Camera Debug Viewer"
echo "========================================"
echo "World  : $WORLD_NAME"
echo "Drone  : ${MODEL_PREFIX}_0"
echo "Thermal: $GAZEBO_THERMAL_CAMERA_TOPIC"
echo

cd "$PROJECT_PATH"
DRONE_ENV="${DRONE_ENV:-$HOME/drone-env}"
source "$DRONE_ENV/bin/activate"
exec python3 -m drone.visualization.sensor_dashboard
