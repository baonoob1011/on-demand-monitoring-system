#!/usr/bin/env bash
set -euo pipefail

PROJECT_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system"
SENSOR_DIR="$PROJECT_PATH/drone/obstacle_avoidance"
LIDAR_TOPIC="${LIDAR_TOPIC:-/lidar}"

echo '========================================'
echo ' LiDAR Sensor Monitor'
echo '========================================'
echo "Topic: $LIDAR_TOPIC"
echo
echo '[LIDAR] Waiting for Gazebo sensor topic...'

for _ in $(seq 1 90); do
    if gz topic -l 2>/dev/null | grep -Fxq "$LIDAR_TOPIC"; then
        echo "[LIDAR] Topic found: $LIDAR_TOPIC"
        cd "$SENSOR_DIR"
        exec python3 lidar_listener.py
    fi
    sleep 1
done

echo "[LIDAR][WARN] Topic not found: $LIDAR_TOPIC"
echo '[LIDAR] Available sensor topics:'
gz topic -l 2>/dev/null | grep -Ei 'lidar|laser|scan|camera|image|imu|sensor' || true
echo
echo '[LIDAR] This screen will stay open for inspection.'
exec bash
