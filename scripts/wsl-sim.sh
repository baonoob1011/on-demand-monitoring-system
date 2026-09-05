#!/usr/bin/env bash
# wsl-sim.sh - PX4 launches Gazebo + drone (non-standalone)
set -e

FOREST3D_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/Forest3D"
PX4_ROOT="$HOME/PX4-Autopilot"
PX4_BUILD="$PX4_ROOT/build/px4_sitl_default"
PX4_GZ_PLUGIN_PATH="$PX4_BUILD/src/modules/simulation/gz_plugins"
PX4_GZ_WORLD_PATH="$PX4_ROOT/Tools/simulation/gz/worlds/forest_monitoring.sdf"

source "$PX4_BUILD/rootfs/gz_env.sh"
export GZ_SIM_RESOURCE_PATH="${FOREST3D_PATH}:$PX4_ROOT/Tools/simulation/gz/models:$PX4_ROOT/Tools/simulation/gz/worlds:${GZ_SIM_RESOURCE_PATH:-}"
export GZ_SIM_SYSTEM_PLUGIN_PATH="${PX4_GZ_PLUGIN_PATH}:${GZ_SIM_SYSTEM_PLUGIN_PATH:-}"
export LD_LIBRARY_PATH="${PX4_GZ_PLUGIN_PATH}:${LD_LIBRARY_PATH:-}"

pkill -u "$USER" -f '[m]avsdk_server' 2>/dev/null || true
pkill -u "$USER" -f '[m]ake px4_sitl'  2>/dev/null || true
pkill -u "$USER" -f '[b]in/px4'        2>/dev/null || true
pkill -u "$USER" -f '[g]z sim'         2>/dev/null || true
sleep 3

# Sync latest Forest3D world to PX4
cp "$FOREST3D_PATH/worlds/forest_monitoring.sdf" "$PX4_GZ_WORLD_PATH"

echo '================================================'
echo ' PX4 is launching Gazebo + spawning x500 drone'
echo ' Wait for: Ready for takeoff!'
echo ' The Gazebo window PX4 opens WILL have the drone'
echo '================================================'

cd "$PX4_ROOT"
PX4_GZ_WORLD=forest_monitoring PX4_GZ_MODEL_POSE="0,0,0.3,0,0,0" make px4_sitl gz_x500
