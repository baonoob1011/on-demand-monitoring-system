#!/usr/bin/env bash
set -eo pipefail

PROJECT_PATH="${PROJECT_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
PX4_ROOT="${PX4_ROOT:-$HOME/PX4-Autopilot}"
PX4_BUILD="$PX4_ROOT/build/px4_sitl_default"

source "$PX4_BUILD/rootfs/gz_env.sh"
set -u

export GZ_SIM_RESOURCE_PATH="$PROJECT_PATH/Forest3D:$PROJECT_PATH/Forest3D/models:$PX4_ROOT/Tools/simulation/gz/models:$PX4_ROOT/Tools/simulation/gz/worlds:${GZ_SIM_RESOURCE_PATH:-}"
export LIBGL_ALWAYS_SOFTWARE=1

exec gz sim -g
