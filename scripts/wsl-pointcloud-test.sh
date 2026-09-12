#!/usr/bin/env bash
set -uo pipefail

PROJECT_PATH="/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system"
TOPIC="${POINTCLOUD_LIDAR_TOPIC:-/lidar_3d}"

cd "$PROJECT_PATH" || exit 1
export PYTHONPATH="$PROJECT_PATH/drone:/usr/lib/python3/dist-packages:${PYTHONPATH:-}"

python3 - <<'PY'
import os
import time

from obstacle_avoidance.pointcloud_gateway import PointCloudGateway


topic = os.getenv("POINTCLOUD_LIDAR_TOPIC", "/lidar_3d")
gateway = PointCloudGateway(topic)

if not gateway.start():
    raise SystemExit(1)

deadline = time.monotonic() + 10.0
while time.monotonic() < deadline:
    snapshot = gateway.snapshot()
    if snapshot is not None:
        points = snapshot.points_body
        print("[POINTCLOUD] ========================================")
        print(f"[POINTCLOUD] topic          : {topic}")
        print(f"[POINTCLOUD] horizontal     : {snapshot.horizontal_count}")
        print(f"[POINTCLOUD] vertical       : {snapshot.vertical_count}")
        print(f"[POINTCLOUD] points         : {len(points)}")
        if len(points):
            mins = points.min(axis=0)
            maxs = points.max(axis=0)
            print(f"[POINTCLOUD] x range        : {mins[0]:.2f} .. {maxs[0]:.2f}")
            print(f"[POINTCLOUD] y range        : {mins[1]:.2f} .. {maxs[1]:.2f}")
            print(f"[POINTCLOUD] z range        : {mins[2]:.2f} .. {maxs[2]:.2f}")
        print("[POINTCLOUD] ========================================")
        raise SystemExit(0)

    time.sleep(0.1)

print(f"[POINTCLOUD] No scan received from {topic}")
raise SystemExit(2)
PY
