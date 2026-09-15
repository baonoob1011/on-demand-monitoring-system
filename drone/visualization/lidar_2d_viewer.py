from __future__ import annotations

import math
import time

import cv2
import numpy as np

from drone.obstacle_avoidance.sensor_reader import (
    EMERGENCY_DISTANCE_M,
    MAX_RANGE_M,
    OBSTACLE_DISTANCE_M,
    WARNING_DISTANCE_M,
    build_obstacle_state,
    front_obstacle_reading,
    get_safety_action,
    index_to_angle,
    sanitize,
)


STATUS_COLORS = {
    "CLEAR": (80, 220, 90),
    "WARNING": (0, 210, 255),
    "OBSTACLE": (0, 130, 255),
    "EMERGENCY": (0, 0, 255),
}


def _put(img, text: str, origin: tuple[int, int], scale: float = 0.55, color=(235, 235, 235)) -> None:
    cv2.putText(img, text, origin, cv2.FONT_HERSHEY_SIMPLEX, scale, color, 1, cv2.LINE_AA)


def draw_lidar_2d(sample, fps: float, topic: str) -> np.ndarray:
    width, height = 760, 520
    img = np.full((height, width, 3), (15, 18, 22), dtype=np.uint8)
    panel_x = 545
    center = (275, 282)
    radius = 198
    max_range = max(MAX_RANGE_M, 1.0)

    cv2.rectangle(img, (0, 0), (width, 42), (20, 150, 45), -1)
    _put(img, "2. Man hinh LiDAR 2D (Radar / Laser Scan)", (15, 28), 0.8, (255, 255, 255))
    cv2.line(img, (panel_x, 42), (panel_x, height), (70, 75, 85), 1)

    for ring in (0.25, 0.5, 0.75, 1.0):
        cv2.circle(img, center, int(radius * ring), (65, 70, 78), 1)
        label = f"{max_range * ring:.0f} m"
        _put(img, label, (center[0] + int(radius * ring) + 4, center[1] - 4), 0.35, (150, 154, 160))

    for angle_deg in range(0, 360, 30):
        rad = math.radians(angle_deg)
        end = (
            int(center[0] + math.sin(rad) * radius),
            int(center[1] - math.cos(rad) * radius),
        )
        cv2.line(img, center, end, (50, 55, 63), 1)

    _put(img, "90deg", (center[0] - 24, center[1] - radius - 10), 0.5)
    _put(img, "180deg", (center[0] - radius - 58, center[1] + 4), 0.5)
    _put(img, "0deg", (center[0] + radius + 14, center[1] + 4), 0.5)
    _put(img, "270deg", (center[0] - 28, center[1] + radius + 25), 0.5)

    status = "WAITING"
    action = "WAITING"
    front_distance = 0.0
    point_count = 0

    if sample is not None:
        ranges = list(sample.ranges)
        point_count = len(ranges)
        state = build_obstacle_state(ranges)
        status, direction, front_distance = front_obstacle_reading(state)
        action = get_safety_action(status)
        color = STATUS_COLORS.get(status, (200, 200, 200))

        for index, raw_value in enumerate(ranges):
            value = sanitize(raw_value)
            if value >= max_range:
                continue
            angle = index_to_angle(index, len(ranges))
            rad = math.radians(angle)
            r = max(2, int((value / max_range) * radius))
            x = int(center[0] - math.sin(rad) * r)
            y = int(center[1] - math.cos(rad) * r)
            cv2.circle(img, (x, y), 2, color, -1)

        for threshold, ring_color in (
            (WARNING_DISTANCE_M, (0, 210, 255)),
            (OBSTACLE_DISTANCE_M, (0, 130, 255)),
            (EMERGENCY_DISTANCE_M, (0, 0, 255)),
        ):
            cv2.circle(img, center, int(min(threshold / max_range, 1.0) * radius), ring_color, 1)

        _put(img, f"Front: {front_distance:.2f} m", (panel_x + 18, 170), 0.55, color)
        _put(img, f"Dir: {direction}", (panel_x + 18, 198), 0.55, color)

    cv2.drawMarker(img, center, (40, 70, 255), cv2.MARKER_TRIANGLE_UP, 18, 2)
    _put(img, f"Topic: {topic}", (panel_x + 18, 95), 0.52, (105, 255, 105))
    _put(img, f"Status: {status}", (panel_x + 18, 124), 0.55, STATUS_COLORS.get(status, (220, 220, 220)))
    _put(img, f"Action: {action}", (panel_x + 18, 144), 0.48)
    _put(img, f"Points: {point_count}", (panel_x + 18, 242), 0.52)
    _put(img, f"FPS: {fps:.1f}", (panel_x + 18, 268), 0.52)
    _put(img, "Same sectors as flight safety", (panel_x + 18, 316), 0.43, (170, 175, 182))
    _put(img, time.strftime("%H:%M:%S"), (panel_x + 18, height - 28), 0.52, (170, 175, 182))
    return img

