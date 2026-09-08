from __future__ import annotations

import math
import os
from dataclasses import dataclass

import numpy as np

from .planner_types import TrajectoryPoint, VelocityCommand


@dataclass(frozen=True)
class TrackerConfig:
    lookahead_s: float = float(os.getenv("PLANNER_TRACKER_LOOKAHEAD_S", "0.25"))
    max_horizontal_speed_m_s: float = float(os.getenv("PLANNER_MAX_HORIZONTAL_SPEED_M_S", "3.0"))
    max_vertical_speed_m_s: float = float(os.getenv("PLANNER_MAX_VERTICAL_SPEED_M_S", "1.5"))


def body_velocity_to_ned(forward: float, right: float, up: float, yaw_deg: float) -> VelocityCommand:
    yaw_rad = math.radians(yaw_deg)
    north = forward * math.cos(yaw_rad) - right * math.sin(yaw_rad)
    east = forward * math.sin(yaw_rad) + right * math.cos(yaw_rad)
    down = -up
    return VelocityCommand(north_m_s=north, east_m_s=east, down_m_s=down, yaw_deg=yaw_deg)


class TrajectoryTracker:
    def __init__(self, config: TrackerConfig | None = None) -> None:
        self.config = config or TrackerConfig()

    def command_at(self, trajectory: list[TrajectoryPoint], elapsed_s: float, yaw_deg: float) -> VelocityCommand | None:
        if not trajectory:
            return None

        target_t = elapsed_s + self.config.lookahead_s
        point = min(trajectory, key=lambda item: abs(item.time_s - target_t))
        velocity = np.asarray(point.velocity, dtype=np.float32)

        horizontal = float(np.linalg.norm(velocity[:2]))
        if horizontal > self.config.max_horizontal_speed_m_s:
            velocity[:2] *= self.config.max_horizontal_speed_m_s / max(horizontal, 1e-6)

        vertical = abs(float(velocity[2]))
        if vertical > self.config.max_vertical_speed_m_s:
            velocity[2] *= self.config.max_vertical_speed_m_s / max(vertical, 1e-6)

        return body_velocity_to_ned(float(velocity[0]), float(velocity[1]), float(velocity[2]), yaw_deg)
