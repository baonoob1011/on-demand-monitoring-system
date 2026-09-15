#!/usr/bin/env bash
set +e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SIM_WORLD="${1:-${SIM_WORLD:-compact}}"

SIM_WORLD="$SIM_WORLD" "$SCRIPT_DIR/wsl-sim.sh" "$SIM_WORLD"
code=$?

echo
echo "[STACK] Gazebo/PX4 exited with code $code."
echo "[STACK] Press Ctrl+D to close this pane, or type:"
echo "        SIM_WORLD=$SIM_WORLD exec $SCRIPT_DIR/wsl-sim.sh $SIM_WORLD"
exec bash
