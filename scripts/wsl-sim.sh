#!/usr/bin/env bash
# wsl-sim.sh - PX4 launches Gazebo + drone (non-standalone)
set -e

PROJECT_PATH="${PROJECT_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
FOREST3D_PATH="${FOREST3D_PATH:-$PROJECT_PATH/Forest3D}"
FOREST3D_MODELS_PATH="${FOREST3D_MODELS_PATH:-$FOREST3D_PATH/models}"
FOREST3D_DRONE_MODEL_PATH="${FOREST3D_DRONE_MODEL_PATH:-$FOREST3D_PATH/models/x500_mono_cam_down}"
ENV_FILE="$PROJECT_PATH/ondemandmonitoring/.env"
PX4_ROOT="$HOME/PX4-Autopilot"
PX4_BUILD="$PX4_ROOT/build/px4_sitl_default"
PX4_GZ_PLUGIN_PATH="$PX4_BUILD/src/modules/simulation/gz_plugins"
FOREST3D_GZ_GUI_CONFIG="$FOREST3D_PATH/gui/forest_monitoring_gui.config"
SIM_WORLD="${1:-${SIM_WORLD:-legacy}}"
PX4_MAVLINK_RC="$PX4_ROOT/ROMFS/px4fmu_common/init.d-posix/px4-rc.mavlink"

if [ -f "$ENV_FILE" ]; then
    set -a
    # Strip Windows BOM/CRLF endings while keeping the source .env unchanged.
    source <(sed '1s/^\xEF\xBB\xBF//; s/\r$//' "$ENV_FILE")
    set +a
fi

FOREST3D_MODELS_PATH="${FOREST3D_MODELS_PATH:-$FOREST3D_PATH/models}"
if [ ! -f "$FOREST3D_MODELS_PATH/compact_terrain/model.config" ] \
    && [ -f "$PROJECT_PATH/Forest3D/models/compact_terrain/model.config" ]; then
    FOREST3D_MODELS_PATH="$PROJECT_PATH/Forest3D/models"
elif [ ! -f "$FOREST3D_MODELS_PATH/compact_terrain/model.config" ] \
    && [ -f "$PROJECT_PATH/drone/Forest3D/models/compact_terrain/model.config" ]; then
    FOREST3D_MODELS_PATH="$PROJECT_PATH/drone/Forest3D/models"
fi
FOREST3D_DRONE_MODEL_PATH="${FOREST3D_DRONE_MODEL_PATH:-$FOREST3D_PATH/models/x500_mono_cam_down}"
if [ ! -f "$FOREST3D_DRONE_MODEL_PATH/model.sdf" ] \
    && [ -f "$PROJECT_PATH/Forest3D/models/x500_mono_cam_down/model.sdf" ]; then
    FOREST3D_DRONE_MODEL_PATH="$PROJECT_PATH/Forest3D/models/x500_mono_cam_down"
fi

PX4_ONBOARD_MAVLINK_RATE_B_S="${PX4_ONBOARD_MAVLINK_RATE_B_S:-100000}"
FOREST3D_WEB_ONLY="${FOREST3D_WEB_ONLY:-1}"

case "$SIM_WORLD" in
    compact)
        WORLD_NAME="forest_monitoring_compact"
        FOREST3D_WORLD_FILE="$FOREST3D_PATH/worlds/forest_monitoring_compact.sdf"
        PX4_GZ_WORLD_PATH="$PX4_ROOT/Tools/simulation/gz/worlds/forest_monitoring_compact.sdf"
        PX4_SPAWN_POSE="0,-280,9.8,0,0,0"
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
export GZ_SIM_RESOURCE_PATH="${FOREST3D_PATH}:${FOREST3D_MODELS_PATH}:$PX4_ROOT/Tools/simulation/gz/models:$PX4_ROOT/Tools/simulation/gz/worlds:${GZ_SIM_RESOURCE_PATH:-}"
export GZ_SIM_SYSTEM_PLUGIN_PATH="${PX4_GZ_PLUGIN_PATH}:${GZ_SIM_SYSTEM_PLUGIN_PATH:-}"
export LD_LIBRARY_PATH="${PX4_GZ_PLUGIN_PATH}:${LD_LIBRARY_PATH:-}"

if [ "$SIM_WORLD" = "compact" ]; then
    backend_candidates=("${BACKEND_BASE_URL:-http://localhost:8080}")
    windows_host="$(awk '/^nameserver / {print $2; exit}' /etc/resolv.conf 2>/dev/null || true)"
    if [ -n "$windows_host" ]; then
        backend_candidates+=("http://${windows_host}:8080")
    fi
    sync_args=()
    for backend_url in "${backend_candidates[@]}"; do
        sync_args+=(--backend-base-url "$backend_url")
    done
    set +e
    python3 "$PROJECT_PATH/tools/sync_thermal_scene.py" \
        "${sync_args[@]}" \
        --output "$FOREST3D_MODELS_PATH/compact_thermal_sources/model.sdf"
    thermal_sync_status=$?
    set -e
    if [ "$thermal_sync_status" -ne 0 ]; then
        echo "[THERMAL] Using last generated heat geometry"
    fi
fi

# Sync selected Forest3D world to PX4.
cp "$FOREST3D_WORLD_FILE" "$PX4_GZ_WORLD_PATH"
mkdir -p "$PX4_ROOT/Tools/simulation/gz/models/x500_mono_cam_down"
if [ ! -f "$FOREST3D_DRONE_MODEL_PATH/model.sdf" ]; then
    echo "[SIM] ERROR: drone model missing: $FOREST3D_DRONE_MODEL_PATH/model.sdf" >&2
    exit 1
fi
cp "$FOREST3D_DRONE_MODEL_PATH/model.sdf" \
    "$PX4_ROOT/Tools/simulation/gz/models/x500_mono_cam_down/model.sdf"
cp "$FOREST3D_DRONE_MODEL_PATH/model.config" \
    "$PX4_ROOT/Tools/simulation/gz/models/x500_mono_cam_down/model.config"

if [ "$SIM_WORLD" = "compact" ]; then
    required_models=(
        compact_terrain
        compact_water
        compact_roads
        compact_bridges
        compact_home
        compact_highrise
        compact_zones
        compact_forest
        compact_thermal_sources
        compact_environment_props
        compact_mountains
        compact_airport
    )
    missing_models=()
    for model in "${required_models[@]}"; do
        if [ -d "$FOREST3D_MODELS_PATH/$model" ]; then
            rm -rf "$PX4_ROOT/Tools/simulation/gz/models/$model"
            cp -R "$FOREST3D_MODELS_PATH/$model" "$PX4_ROOT/Tools/simulation/gz/models/$model"
        else
            missing_models+=("$model")
        fi
    done
    if [ "${#missing_models[@]}" -gt 0 ]; then
        echo "[SIM] ERROR: compact world models missing from $FOREST3D_MODELS_PATH:" >&2
        printf '  - %s\n' "${missing_models[@]}" >&2
        exit 1
    fi
fi

# MAVSDK uses PX4's onboard-payload MAVLink endpoint at UDP 14030.
# Gazebo can run at a low real-time factor on this machine. PX4 schedules
# stream rates in simulation time, while MAVSDK heartbeat timeout is wall-clock.
# Keep only this MAVSDK-facing HEARTBEAT stream high enough to stay above the
# timeout threshold even when SITL runs at roughly 5-10% real time.
if [ -f "$PX4_MAVLINK_RC" ]; then
    python3 - "$PX4_MAVLINK_RC" "$PX4_ONBOARD_MAVLINK_RATE_B_S" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
rate = sys.argv[2]
new = f"mavlink start -x -u $udp_onboard_payload_port_local -r {rate} -f -m onboard -o $udp_onboard_payload_port_remote $mavlink_network_interface_arg"
text = path.read_text()
pattern = re.compile(
    r"mavlink start -x -u \$udp_onboard_payload_port_local -r \d+ "
    r"-f -m onboard -o \$udp_onboard_payload_port_remote "
    r"\$mavlink_network_interface_arg"
)
updated, count = pattern.subn(new, text, count=1)
updated = re.sub(
    r"\n\s*mavlink stream -r \d+(?:\.\d+)? -s HEARTBEAT -u \$udp_onboard_payload_port_local\s*",
    "\n",
    updated,
)
if count != 1:
    print(f"[SIM] ERROR: expected one onboard MAVLink start line in {path}, found {count}", file=sys.stderr)
    sys.exit(1)
heartbeat = "mavlink stream -r 20 -s HEARTBEAT -u $udp_onboard_payload_port_local"
if heartbeat not in updated:
    updated = updated.replace(new, f"{new}\n{heartbeat}", 1)
if count and updated != text:
    path.write_text(updated)
print(new)
print(heartbeat)
PY
    echo "[SIM] PX4 onboard MAVLink 14030 rate target: ${PX4_ONBOARD_MAVLINK_RATE_B_S} B/s"
else
    echo "[SIM] ERROR: PX4 MAVLink rc file not found: $PX4_MAVLINK_RC" >&2
    exit 1
fi

echo '========================================'
echo ' Starting PX4 + Gazebo + Drone'
echo " World : $WORLD_NAME"
echo ' Drone : x500_mono_cam_down'
echo " Pose  : $PX4_SPAWN_POSE"
if [ "$FOREST3D_WEB_ONLY" = "1" ]; then
    export HEADLESS=1
    echo " GUI   : disabled (web UI live view)"
else
    unset HEADLESS
    echo " GUI   : $FOREST3D_GZ_GUI_CONFIG"
fi
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
