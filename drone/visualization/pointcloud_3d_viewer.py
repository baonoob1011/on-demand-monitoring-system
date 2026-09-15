from __future__ import annotations

import os

import cv2
import numpy as np


MAX_POINTS = int(os.getenv("SENSOR_VIS_POINTCLOUD_MAX_POINTS", "30000"))


def _put(img, text: str, origin: tuple[int, int], scale: float = 0.55, color=(235, 235, 235)) -> None:
    cv2.putText(img, text, origin, cv2.FONT_HERSHEY_SIMPLEX, scale, color, 1, cv2.LINE_AA)


def filter_self_points(points: np.ndarray) -> np.ndarray:
    if points.size == 0:
        return points

    radius = float(os.getenv("POINTCLOUD_SELF_FILTER_RADIUS_M", "2.5"))
    half_height = float(os.getenv("POINTCLOUD_SELF_FILTER_HALF_HEIGHT_M", "2.0"))
    finite = np.isfinite(points).all(axis=1)
    horizontal = np.hypot(points[:, 0], points[:, 1])
    outside_self = (horizontal > radius) | (np.abs(points[:, 2]) > half_height)
    return points[finite & outside_self]


def _downsample(points: np.ndarray) -> np.ndarray:
    if points.shape[0] <= MAX_POINTS:
        return points
    step = max(int(np.ceil(points.shape[0] / MAX_POINTS)), 1)
    return points[::step][:MAX_POINTS]


def _project(points: np.ndarray, width: int, height: int) -> tuple[np.ndarray, np.ndarray]:
    yaw = np.deg2rad(-35.0)
    pitch = np.deg2rad(24.0)
    cy, sy = np.cos(yaw), np.sin(yaw)
    cp, sp = np.cos(pitch), np.sin(pitch)

    x = points[:, 0]
    y = points[:, 1]
    z = points[:, 2]
    xr = x * cy - y * sy
    yr = x * sy + y * cy
    zr = z
    yp = yr * cp - zr * sp
    zp = yr * sp + zr * cp

    scale = 8.0
    sx = (width * 0.44 + xr * scale).astype(np.int32)
    sy_screen = (height * 0.68 - yp * scale - zp * 2.0).astype(np.int32)
    distance = np.linalg.norm(points, axis=1)
    return np.stack((sx, sy_screen), axis=1), distance


def draw_pointcloud_3d(points: np.ndarray | None, fps: float, topic: str, raw_count: int = 0) -> np.ndarray:
    width, height = 900, 520
    img = np.full((height, width, 3), (13, 17, 23), dtype=np.uint8)
    panel_x = 700

    cv2.rectangle(img, (0, 0), (width, 42), (118, 28, 164), -1)
    _put(img, "3. Man hinh LiDAR 3D (Point Cloud)", (15, 28), 0.8, (255, 255, 255))
    cv2.line(img, (panel_x, 42), (panel_x, height), (70, 75, 85), 1)

    origin = (360, 355)
    for offset in range(-280, 281, 40):
        cv2.line(img, (origin[0] + offset, 130), (origin[0] + offset, height - 35), (45, 51, 60), 1)
        cv2.line(img, (70, origin[1] + offset // 3), (panel_x - 20, origin[1] + offset // 3), (45, 51, 60), 1)

    shown = 0
    max_dist = float(os.getenv("POINTCLOUD_MAX_RANGE_M", "50.0"))
    max_dist = min(max(max_dist, 1.0), 80.0)

    if points is not None and points.size:
        filtered = _downsample(filter_self_points(points))
        shown = filtered.shape[0]
        projected, distances = _project(filtered, width, height)
        valid = (
            (projected[:, 0] >= 0)
            & (projected[:, 0] < panel_x - 4)
            & (projected[:, 1] >= 44)
            & (projected[:, 1] < height)
        )
        projected = projected[valid]
        distances = distances[valid]
        colors = np.clip((distances / max_dist) * 255, 0, 255).astype(np.uint8)
        for (x, y), value in zip(projected, colors):
            color = cv2.applyColorMap(np.array([[255 - value]], dtype=np.uint8), cv2.COLORMAP_JET)[0, 0]
            cv2.circle(img, (int(x), int(y)), 1, tuple(int(c) for c in color), -1)

    cv2.drawMarker(img, origin, (255, 255, 255), cv2.MARKER_CROSS, 28, 2)
    _put(img, "DRONE", (origin[0] - 28, origin[1] + 28), 0.42)
    _put(img, f"Topic: {topic}", (panel_x + 18, 95), 0.52, (105, 255, 105))
    _put(img, f"Raw points: {raw_count}", (panel_x + 18, 126), 0.52)
    _put(img, f"Shown: {shown}", (panel_x + 18, 154), 0.52)
    _put(img, f"FPS: {fps:.1f}", (panel_x + 18, 182), 0.52)
    _put(img, "Self filter: ON", (panel_x + 18, 230), 0.5, (180, 220, 255))
    _put(img, f"Max draw: {MAX_POINTS}", (panel_x + 18, 258), 0.5)

    bar_x = panel_x + 55
    for y in range(315, 455):
        value = int(255 * (1.0 - (y - 315) / 140.0))
        color = cv2.applyColorMap(np.array([[value]], dtype=np.uint8), cv2.COLORMAP_JET)[0, 0]
        cv2.line(img, (bar_x, y), (bar_x + 20, y), tuple(int(c) for c in color), 1)
    _put(img, f"{max_dist:.0f} m", (bar_x + 32, 323), 0.45)
    _put(img, "0 m", (bar_x + 32, 455), 0.45)
    return img
