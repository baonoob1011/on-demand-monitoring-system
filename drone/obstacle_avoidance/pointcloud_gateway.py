from __future__ import annotations

from dataclasses import dataclass
import importlib
import math
import os
import threading
from typing import Iterable

import numpy as np


POINTCLOUD_TOPIC = os.getenv("POINTCLOUD_LIDAR_TOPIC", "/lidar_3d")
POINTCLOUD_MAX_RANGE_M = float(os.getenv("POINTCLOUD_MAX_RANGE_M", "60.0"))


@dataclass(frozen=True)
class PointCloudSnapshot:
    points_body: np.ndarray
    stamp_seq: int
    horizontal_count: int
    vertical_count: int


def load_gazebo_transport():
    last_error = None

    for module_name in ("gz.transport13",):
        try:
            module = importlib.import_module(module_name)
            return module.Node, None
        except ImportError as exc:
            last_error = exc

    return None, last_error


def load_laser_scan_type():
    last_error = None

    for module_name in (
        "gz.msgs10.laserscan_pb2",
        "gz.msgs10.laser_scan_pb2",
    ):
        try:
            module = importlib.import_module(module_name)
            if hasattr(module, "LaserScan"):
                return module.LaserScan, None
        except ImportError as exc:
            last_error = exc

    return None, last_error


Node, NODE_IMPORT_ERROR = load_gazebo_transport()
LaserScan, LASER_SCAN_IMPORT_ERROR = load_laser_scan_type()


def native_import_error() -> Exception | str | None:
    if Node is None:
        return NODE_IMPORT_ERROR or "gz.transport13.Node not found"
    if LaserScan is None:
        return LASER_SCAN_IMPORT_ERROR or "LaserScan protobuf type not found"
    return None


def _axis_angles(count: int, min_angle: float, max_angle: float, step: float) -> np.ndarray:
    if count <= 1:
        return np.array([min_angle], dtype=np.float32)

    if step > 0:
        return min_angle + np.arange(count, dtype=np.float32) * float(step)

    return np.linspace(min_angle, max_angle, count, dtype=np.float32)


def laserscan_to_points_body(msg, max_range_m: float = POINTCLOUD_MAX_RANGE_M) -> np.ndarray:
    """
    Convert Gazebo LaserScan ranges to BODY-frame XYZ points.

    BODY convention:
        X = forward
        Y = right
        Z = up

    Gazebo scan angle convention is treated as positive-left around Z, so
    BODY Y is negated to keep positive-right for the planner.
    """

    ranges = np.asarray(list(msg.ranges), dtype=np.float32)
    if ranges.size == 0:
        return np.empty((0, 3), dtype=np.float32)

    horizontal_count = int(getattr(msg, "count", 0) or ranges.size)
    vertical_count = int(getattr(msg, "vertical_count", 0) or 1)
    expected_count = horizontal_count * vertical_count

    if expected_count <= 0:
        return np.empty((0, 3), dtype=np.float32)

    usable_count = min(ranges.size, expected_count)
    ranges = ranges[:usable_count]

    finite = np.isfinite(ranges)
    range_min = float(getattr(msg, "range_min", 0.0) or 0.0)
    range_max = min(float(getattr(msg, "range_max", max_range_m) or max_range_m), max_range_m)
    valid = finite & (ranges >= range_min) & (ranges <= range_max)

    if not np.any(valid):
        return np.empty((0, 3), dtype=np.float32)

    horizontal_angles = _axis_angles(
        horizontal_count,
        float(getattr(msg, "angle_min", -math.pi)),
        float(getattr(msg, "angle_max", math.pi)),
        float(getattr(msg, "angle_step", 0.0) or 0.0),
    )
    vertical_angles = _axis_angles(
        vertical_count,
        float(getattr(msg, "vertical_angle_min", 0.0) or 0.0),
        float(getattr(msg, "vertical_angle_max", 0.0) or 0.0),
        float(getattr(msg, "vertical_angle_step", 0.0) or 0.0),
    )

    vertical_index = np.arange(usable_count, dtype=np.int32) // horizontal_count
    horizontal_index = np.arange(usable_count, dtype=np.int32) % horizontal_count

    yaw = horizontal_angles[horizontal_index]
    pitch = vertical_angles[vertical_index]
    distance = ranges

    cos_pitch = np.cos(pitch)
    x = distance * cos_pitch * np.cos(yaw)
    y = -(distance * cos_pitch * np.sin(yaw))
    z = distance * np.sin(pitch)

    return np.stack((x[valid], y[valid], z[valid]), axis=1).astype(np.float32)


class PointCloudGateway:
    def __init__(self, topic: str = POINTCLOUD_TOPIC):
        self.topic = topic
        self.node = None
        self.lock = threading.Lock()
        self.available = False
        self._snapshot: PointCloudSnapshot | None = None
        self._stamp_seq = 0

    def start(self) -> bool:
        import_error = native_import_error()
        if import_error is not None:
            print(f"[POINTCLOUD] Gazebo transport unavailable: {import_error}", flush=True)
            return False

        try:
            self.node = Node()
            self.node.subscribe(
                LaserScan,
                self.topic,
                self._on_scan,
            )
        except Exception as exc:
            print(f"[POINTCLOUD] Subscribe failed: {exc}", flush=True)
            return False

        self.available = True
        print(f"[POINTCLOUD] Listening: {self.topic}", flush=True)
        return True

    def _on_scan(self, msg: LaserScan, *_args) -> None:
        points = laserscan_to_points_body(msg)
        horizontal_count = int(getattr(msg, "count", 0) or 0)
        vertical_count = int(getattr(msg, "vertical_count", 0) or 1)

        with self.lock:
            self._stamp_seq += 1
            self._snapshot = PointCloudSnapshot(
                points_body=points,
                stamp_seq=self._stamp_seq,
                horizontal_count=horizontal_count,
                vertical_count=vertical_count,
            )

    def snapshot(self) -> PointCloudSnapshot | None:
        with self.lock:
            return self._snapshot


__all__ = [
    "POINTCLOUD_TOPIC",
    "PointCloudGateway",
    "PointCloudSnapshot",
    "laserscan_to_points_body",
]
