from __future__ import annotations

import os
import time

import cv2

from drone.visualization.gazebo_streams import GazeboLaserScanStream, topic_list
from drone.visualization.lidar_2d_viewer import draw_lidar_2d
from drone.visualization.pointcloud_3d_viewer import draw_pointcloud_3d


WINDOW_2D = "2. Man hinh LiDAR 2D (Radar / Laser Scan)"
WINDOW_3D = "3. Man hinh LiDAR 3D (Point Cloud)"


def _env_bool(name: str, default: str = "true") -> bool:
    return os.getenv(name, default).strip().lower() in {"1", "true", "yes", "on"}


def _resolve_topics() -> tuple[str, str]:
    topics = topic_list()
    lidar_2d = os.getenv("LIDAR_TOPIC", "/lidar")
    lidar_3d = os.getenv("POINTCLOUD_LIDAR_TOPIC", "/lidar_3d")
    if lidar_2d not in topics:
        lidar_2d = next((topic for topic in topics if topic.endswith("/lidar") or "scan" in topic.lower()), lidar_2d)
    if lidar_3d not in topics:
        lidar_3d = next((topic for topic in topics if "lidar_3d" in topic.lower() or "points" in topic.lower()), lidar_3d)
    return lidar_2d, lidar_3d


def _fps(last_time: float, current: float, previous_fps: float) -> float:
    if last_time <= 0:
        return previous_fps
    instant = 1.0 / max(current - last_time, 0.001)
    if previous_fps <= 0:
        return instant
    return previous_fps * 0.85 + instant * 0.15


def main() -> int:
    if not _env_bool("SENSOR_VIS_ENABLED"):
        print("[SENSOR-VIS] Disabled by SENSOR_VIS_ENABLED=false", flush=True)
        return 0

    lidar_2d_enabled = _env_bool("SENSOR_VIS_LIDAR_2D_ENABLED")
    lidar_3d_enabled = _env_bool("SENSOR_VIS_LIDAR_3D_ENABLED")
    lidar_2d_topic, lidar_3d_topic = _resolve_topics()

    print("========================================", flush=True)
    print(" Forest3D Sensor Visual Dashboard", flush=True)
    print("========================================", flush=True)
    print(f"LiDAR 2D: {lidar_2d_topic} enabled={lidar_2d_enabled}", flush=True)
    print(f"LiDAR 3D: {lidar_3d_topic} enabled={lidar_3d_enabled}", flush=True)
    print("Camera view is opened by scripts/wsl-camera-view.sh", flush=True)

    errors: list[str] = []
    stream_2d = GazeboLaserScanStream(lidar_2d_topic, 360, "LiDAR 2D", errors.append)
    stream_3d = GazeboLaserScanStream(lidar_3d_topic, 7200, "LiDAR 3D", errors.append)

    if lidar_2d_enabled:
        stream_2d.start()
        cv2.namedWindow(WINDOW_2D, cv2.WINDOW_NORMAL)
        cv2.resizeWindow(WINDOW_2D, 760, 520)
        cv2.moveWindow(WINDOW_2D, 0, 560)
    if lidar_3d_enabled:
        stream_3d.start()
        cv2.namedWindow(WINDOW_3D, cv2.WINDOW_NORMAL)
        cv2.resizeWindow(WINDOW_3D, 900, 520)
        cv2.moveWindow(WINDOW_3D, 760, 560)

    last_2d_draw = 0.0
    last_3d_draw = 0.0
    fps_2d = 0.0
    fps_3d = 0.0
    target_2d = 1.0 / max(float(os.getenv("SENSOR_VIS_LIDAR_2D_FPS", "15")), 1.0)
    target_3d = 1.0 / max(float(os.getenv("SENSOR_VIS_LIDAR_3D_FPS", "8")), 1.0)

    try:
        while True:
            now = time.monotonic()

            if lidar_2d_enabled and now - last_2d_draw >= target_2d:
                _seq, sample = stream_2d.snapshot()
                fps_2d = _fps(last_2d_draw, now, fps_2d)
                last_2d_draw = now
                cv2.imshow(WINDOW_2D, draw_lidar_2d(sample, fps_2d, lidar_2d_topic))

            if lidar_3d_enabled and now - last_3d_draw >= target_3d:
                _seq, sample = stream_3d.snapshot()
                points = sample.to_pointcloud() if sample is not None else None
                raw_count = len(sample.ranges) if sample is not None else 0
                fps_3d = _fps(last_3d_draw, now, fps_3d)
                last_3d_draw = now
                cv2.imshow(WINDOW_3D, draw_pointcloud_3d(points, fps_3d, lidar_3d_topic, raw_count))

            if errors:
                print(f"[SENSOR-VIS] {errors.pop(0)}", flush=True)

            key = cv2.waitKey(10) & 0xFF
            if key in (27, ord("q")):
                break
    finally:
        stream_2d.stop()
        stream_3d.stop()
        cv2.destroyAllWindows()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

