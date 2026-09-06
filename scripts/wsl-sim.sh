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

    pkill -9 -f '[p]x4' 2>/dev/null || true
    pkill -9 -f '[g]z' 2>/dev/null || true
    pkill -9 -f '[r]uby' 2>/dev/null || true

    exit "$status"
}

trap cleanup EXIT INT TERM

echo '========================================'
echo ' Cleaning previous drone simulation...'
echo '========================================'

pkill -9 -f '[p]x4' 2>/dev/null || true
pkill -9 -f '[g]z' 2>/dev/null || true
pkill -9 -f '[r]uby' 2>/dev/null || true

sleep 2

source "$PX4_BUILD/rootfs/gz_env.sh"
export GZ_SIM_RESOURCE_PATH="${FOREST3D_PATH}:${FOREST3D_PATH}/models:$PX4_ROOT/Tools/simulation/gz/models:$PX4_ROOT/Tools/simulation/gz/worlds:${GZ_SIM_RESOURCE_PATH:-}"
export GZ_SIM_SYSTEM_PLUGIN_PATH="${PX4_GZ_PLUGIN_PATH}:${GZ_SIM_SYSTEM_PLUGIN_PATH:-}"
export LD_LIBRARY_PATH="${PX4_GZ_PLUGIN_PATH}:${LD_LIBRARY_PATH:-}"

# Sync latest Forest3D world to PX4
cp "$FOREST3D_PATH/worlds/forest_monitoring.sdf" "$PX4_GZ_WORLD_PATH"

echo '========================================'
echo ' Starting PX4 + Gazebo + Blender World'
echo ' World : forest_monitoring'
echo ' Drone : x500_mono_cam_down'
echo ' Pose  : 0,0,0.45,0,0,0'
echo '========================================'

# Background: keep the main Gazebo camera following the spawned drone.
# PX4 can take a different amount of time to spawn the model, so wait for it
# instead of relying on one fixed sleep.
(
    sleep 8
    drone_model=""
    for _ in $(seq 1 60); do
        if ! pgrep -u "$USER" -f 'gz sim .* -g|gz sim -g' >/dev/null 2>&1; then
            echo "[SIM] Main Gazebo GUI is not running; reopening Gazebo Sim GUI..."
            gz sim -g >/tmp/gz-sim-gui.log 2>&1 &
            sleep 5
        fi

        drone_model="$(gz model --list 2>/dev/null | sed 's/^[[:space:]]*-[[:space:]]*//' | grep -m1 '^x500_mono_cam_down' || true)"
        if [ -n "$drone_model" ]; then
            break
        fi
        sleep 1
    done

    if [ -z "$drone_model" ]; then
        echo "[SIM] Camera follow skipped: drone model not found yet"
        exit 0
    fi

    echo "[SIM] Configuring main Gazebo camera to follow $drone_model..."
    for _ in $(seq 1 5); do
        gz service -s /gui/camera/follow \
            --reqtype gz.msgs.StringMsg \
            --reptype gz.msgs.Boolean \
            --timeout 5000 \
            --req "data: \"$drone_model\"" 2>/dev/null || true
        sleep 1
        gz service -s /gui/camera/follow/offset \
            --reqtype gz.msgs.Vector3d \
            --reptype gz.msgs.Boolean \
            --timeout 5000 \
            --req 'x: -9.0, y: 0.0, z: 4.5' 2>/dev/null || true
        gz service -s /gui/camera/follow/p_gain \
            --reqtype gz.msgs.Double \
            --reptype gz.msgs.Boolean \
            --timeout 5000 \
            --req 'data: 0.12' 2>/dev/null || true
        sleep 2
    done
    echo "[SIM] Camera follow set: entity=$drone_model  offset=(-9,0,4.5)  p_gain=0.12"
) &

cd "$PX4_ROOT"
PX4_GZ_WORLD=forest_monitoring \
PX4_GZ_MODEL_POSE="0,0,0.45,0,0,0" \
make px4_sitl gz_x500_mono_cam_down
