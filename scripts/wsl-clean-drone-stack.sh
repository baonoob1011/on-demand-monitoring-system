#!/usr/bin/env bash
set -euo pipefail

echo '========================================'
echo ' Cleaning previous drone simulation...'
echo '========================================'

pkill -9 -x px4 || true
pkill -9 -x gz || true
pkill -9 -x ruby || true
pkill -9 -u "$USER" -x mavsdk_server 2>/dev/null || true
rm -f /tmp/forest3d_sitl_battery_state.json /tmp/forest3d_sitl_battery_state.tmp || true

sleep 2

if pgrep -u "$USER" -x 'px4|gz|ruby' >/dev/null 2>&1; then
    echo '[WARN] Some PX4/Gazebo/Ruby processes are still running:'
    pgrep -a -u "$USER" -x 'px4|gz|ruby' || true
else
    echo '[OK] Old PX4/Gazebo/Ruby processes stopped.'
fi
