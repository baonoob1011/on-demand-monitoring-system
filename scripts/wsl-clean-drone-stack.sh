#!/usr/bin/env bash
set -euo pipefail

echo '========================================'
echo ' Cleaning previous drone simulation...'
echo '========================================'

pkill -9 -f px4 || true
pkill -9 -f gz || true
pkill -9 -f ruby || true
pkill -9 -u "$USER" -f '[m]avsdk_server' 2>/dev/null || true

sleep 2

if pgrep -u "$USER" -f '[p]x4|[g]z|[r]uby' >/dev/null 2>&1; then
    echo '[WARN] Some PX4/Gazebo/Ruby processes are still running:'
    pgrep -a -u "$USER" -f '[p]x4|[g]z|[r]uby' || true
else
    echo '[OK] Old PX4/Gazebo/Ruby processes stopped.'
fi
