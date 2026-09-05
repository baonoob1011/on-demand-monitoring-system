#!/usr/bin/env bash
# wsl-sim.sh - PX4 launches Gazebo + drone (non-standalone)
set -e

FOREST3D_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/Forest3D"
PX4_ROOT="$HOME/PX4-Autopilot"
PX4_BUILD="$PX4_ROOT/build/px4_sitl_default"
PX4_GZ_PLUGIN_PATH="$PX4_BUILD/src/modules/simulation/gz_plugins"
PX4_GZ_WORLD_PATH="$PX4_ROOT/Tools/simulation/gz/worlds/forest_monitoring.sdf"

cleanup() {
    status=$?
    trap - EXIT INT TERM

    echo
    echo '========================================'
    echo ' Stopping PX4/Gazebo child processes...'
    echo '========================================'

    pkill -9 -f px4 2>/dev/null || true
    pkill -9 -f gz 2>/dev/null || true
    pkill -9 -f ruby 2>/dev/null || true

    exit "$status"
}

trap cleanup EXIT INT TERM

source "$PX4_BUILD/rootfs/gz_env.sh"
export GZ_SIM_RESOURCE_PATH="${FOREST3D_PATH}:$PX4_ROOT/Tools/simulation/gz/models:$PX4_ROOT/Tools/simulation/gz/worlds:${GZ_SIM_RESOURCE_PATH:-}"
export GZ_SIM_SYSTEM_PLUGIN_PATH="${PX4_GZ_PLUGIN_PATH}:${GZ_SIM_SYSTEM_PLUGIN_PATH:-}"
export LD_LIBRARY_PATH="${PX4_GZ_PLUGIN_PATH}:${LD_LIBRARY_PATH:-}"

# Sync latest Forest3D world to PX4
cp "$FOREST3D_PATH/worlds/forest_monitoring.sdf" "$PX4_GZ_WORLD_PATH"

echo '========================================'
echo ' Starting PX4 + Gazebo + Drone...'
echo ' World: forest_monitoring'
echo ' Model: x500_mono_cam_down'
echo ' Pose: 0,0,0.3,0,0,0'
echo '========================================'

cd "$PX4_ROOT"
PX4_GZ_WORLD=forest_monitoring PX4_GZ_MODEL_POSE="0,0,0.3,0,0,0" make px4_sitl gz_x500_mono_cam_down
