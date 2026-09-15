#!/usr/bin/env bash
set -euo pipefail

PROJECT_PATH="${PROJECT_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
PX4_ROOT="${PX4_ROOT:-$HOME/PX4-Autopilot}"
DRONE_WORKDIR="${DRONE_WORKDIR:-$HOME/drone-controller}"
DRONE_ENV="${DRONE_ENV:-$HOME/drone-env}"
MARKER_DIR="$HOME/.cache/ondemand-drone-stack"
MARKER_FILE="$MARKER_DIR/bootstrap-v1.done"

log() {
    printf '\n==> %s\n' "$1"
}

need_cmd() {
    command -v "$1" >/dev/null 2>&1
}

PX4_BUILD_READY=0
if [ -f "$PX4_ROOT/Tools/setup/ubuntu.sh" ] \
    && [ -f "$PX4_ROOT/build/px4_sitl_default/rootfs/gz_env.sh" ]; then
    PX4_BUILD_READY=1
fi

PYTHON_ENV_READY=0
if [ -x "$DRONE_ENV/bin/python" ] \
    && "$DRONE_ENV/bin/python" - <<'PY' >/dev/null 2>&1
import cv2
import grpc
import httpx
import mavsdk
from PIL import Image
import dotenv
PY
then
    PYTHON_ENV_READY=1
fi

if [ "$PX4_BUILD_READY" -eq 1 ] && [ "$PYTHON_ENV_READY" -eq 1 ]; then
    mkdir -p "$DRONE_WORKDIR/video" "$MARKER_DIR"
    date -Is > "$MARKER_FILE"
    echo "[BOOTSTRAP] Existing drone stack dependencies found"
    exit 0
fi

log "Installing Ubuntu packages"
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    ca-certificates \
    curl \
    git \
    lsb-release \
    python3 \
    python3-pip \
    python3-venv \
    rsync \
    unzip \
    wget

if [ ! -d "$PX4_ROOT/.git" ]; then
    log "Downloading PX4 Autopilot"
    git clone --recursive https://github.com/PX4/PX4-Autopilot.git "$PX4_ROOT"
else
    log "Updating PX4 Autopilot submodules"
    git -C "$PX4_ROOT" submodule update --init --recursive
fi

log "Installing PX4 and Gazebo dependencies"
bash "$PX4_ROOT/Tools/setup/ubuntu.sh" --no-nuttx

log "Preparing Python controller environment"
python3 -m venv "$DRONE_ENV"
# shellcheck disable=SC1090
source "$DRONE_ENV/bin/activate"
python -m pip install --upgrade pip
python -m pip install \
    grpcio \
    httpx \
    mavsdk \
    numpy \
    opencv-python \
    pillow \
    python-dotenv

log "Preparing controller working folder"
mkdir -p "$DRONE_WORKDIR/video" "$MARKER_DIR"

log "Building PX4 SITL once"
make -C "$PX4_ROOT" px4_sitl gz_x500_mono_cam_down

date -Is > "$MARKER_FILE"
echo "[BOOTSTRAP] Drone stack dependencies are ready"
