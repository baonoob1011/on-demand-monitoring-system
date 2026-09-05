#!/usr/bin/env bash
set -e

sleep 22

REPO_CONTROLLER="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone-controller"
LEGACY_ENV="$HOME/drone-controller/.env"

cd "$REPO_CONTROLLER"
if [ -f "$LEGACY_ENV" ] && [ ! -f .env ]; then
    set -a
    # Reuse the existing local runtime config while running the repo version of the code.
    source "$LEGACY_ENV"
    set +a
fi

source ~/drone-env/bin/activate
python telemetry_sender.py
