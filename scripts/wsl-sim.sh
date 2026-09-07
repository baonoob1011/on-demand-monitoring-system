#!/usr/bin/env bash
# wsl-sim.sh - PX4 launches Gazebo + drone (non-standalone)
set -e

FOREST3D_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/Forest3D"
PROJECT_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system"
PX4_ROOT="$HOME/PX4-Autopilot"
PX4_BUILD="$PX4_ROOT/build/px4_sitl_default"
PX4_GZ_PLUGIN_PATH="$PX4_BUILD/src/modules/simulation/gz_plugins"
FOREST3D_GZ_GUI_CONFIG="$FOREST3D_PATH/gui/forest_monitoring_gui.config"
SIM_WORLD="${1:-${SIM_WORLD:-legacy}}"

case "$SIM_WORLD" in
    compact)
        WORLD_NAME="forest_monitoring_compact"
        FOREST3D_WORLD_FILE="$FOREST3D_PATH/worlds/forest_monitoring_compact.sdf"
        PX4_GZ_WORLD_PATH="$PX4_ROOT/Tools/simulation/gz/worlds/forest_monitoring_compact.sdf"
        PX4_SPAWN_POSE="0,-280,15.0,0,0,0"
        ;;
    legacy)
        WORLD_NAME="forest_monitoring"
        FOREST3D_WORLD_FILE="$FOREST3D_PATH/worlds/forest_monitoring.sdf"
        PX4_GZ_WORLD_PATH="$PX4_ROOT/Tools/simulation/gz/worlds/forest_monitoring.sdf"
        PX4_SPAWN_POSE="0,0,0.3,0,0,0"
        ;;
    *)
        echo "Usage: $0 [legacy|compact]"
        exit 2
        ;;
esac

source "$PX4_BUILD/rootfs/gz_env.sh"
export PATH="$PROJECT_PATH/scripts/wsl-bin:$PATH"
export FOREST3D_GZ_GUI_CONFIG
export GZ_SIM_RESOURCE_PATH="${FOREST3D_PATH}:${FOREST3D_PATH}/models:$PX4_ROOT/Tools/simulation/gz/models:$PX4_ROOT/Tools/simulation/gz/worlds:${GZ_SIM_RESOURCE_PATH:-}"
export GZ_SIM_SYSTEM_PLUGIN_PATH="${PX4_GZ_PLUGIN_PATH}:${GZ_SIM_SYSTEM_PLUGIN_PATH:-}"
export LD_LIBRARY_PATH="${PX4_GZ_PLUGIN_PATH}:${LD_LIBRARY_PATH:-}"

# Sync selected Forest3D world to PX4.
cp "$FOREST3D_WORLD_FILE" "$PX4_GZ_WORLD_PATH"

echo '========================================'
echo ' Starting PX4 + Gazebo + Drone'
echo " World : $WORLD_NAME"
echo ' Drone : x500_mono_cam_down'
echo " Pose  : $PX4_SPAWN_POSE"
echo " GUI   : $FOREST3D_GZ_GUI_CONFIG"
echo '========================================'

(
    sleep 8
    drone_model=""
    for _ in $(seq 1 60); do
        drone_model="$(gz model --list 2>/dev/null | sed 's/^[[:space:]]*-[[:space:]]*//' | grep -m1 '^x500_mono_cam_down' || true)"
        if [ -n "$drone_model" ]; then
            break
        fi
        sleep 1
    done

    if [ -z "$drone_model" ]; then
        echo "[SIM] Camera follow skipped: drone model not found"
        exit 0
    fi

    for _ in $(seq 1 30); do
        if gz topic -e -t "/world/${WORLD_NAME}/scene/info" -n 1 2>/dev/null | grep -q "$drone_model"; then
            break
        fi
        sleep 1
    done

    echo "[SIM] Setting main Gazebo camera follow: $drone_model"
    follow_ready=0
    for _ in $(seq 1 20); do
        if ! gz service -l 2>/dev/null | grep -qx '/gui/follow'; then
            sleep 1
            continue
        fi
        follow_ready=1

        gz service -s /gui/follow \
            --reqtype gz.msgs.StringMsg \
            --reptype gz.msgs.Boolean \
            --timeout 3000 \
            --req "data: \"$drone_model\"" \
            >/dev/null 2>&1 || true

        gz service -s /gui/follow/offset \
            --reqtype gz.msgs.Vector3d \
            --reptype gz.msgs.Boolean \
            --timeout 3000 \
            --req "x: -8 y: 0 z: 4" \
            >/dev/null 2>&1 && {
                echo "[SIM] Main Gazebo camera is following: $drone_model"
                break
            }
        sleep 1
    done

    if [ "$follow_ready" -eq 0 ]; then
        echo "[SIM] Camera follow skipped: /gui/follow service not ready"
    fi
) &

cd "$PX4_ROOT"
PX4_GZ_WORLD="$WORLD_NAME" \
PX4_GZ_MODEL_POSE="$PX4_SPAWN_POSE" \
make px4_sitl gz_x500_mono_cam_down
