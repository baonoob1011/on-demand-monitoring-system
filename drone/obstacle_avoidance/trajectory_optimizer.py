from __future__ import annotations

import math
import os
import time
from dataclasses import dataclass

import numpy as np

from .local_map_3d import RollingLocalMap3D
from .planner_types import TrajectoryPoint


@dataclass(frozen=True)
class TrajectoryConfig:
    sample_dt_s: float = float(os.getenv("PLANNER_TRAJECTORY_DT_S", "0.1"))
    max_horizontal_speed_m_s: float = float(os.getenv("PLANNER_MAX_HORIZONTAL_SPEED_M_S", "3.0"))
    max_vertical_speed_m_s: float = float(os.getenv("PLANNER_MAX_VERTICAL_SPEED_M_S", "1.5"))
    max_accel_m_s2: float = float(os.getenv("PLANNER_MAX_ACCEL_M_S2", "2.0"))
    max_jerk_m_s3: float = float(os.getenv("PLANNER_MAX_JERK_M_S3", "4.0"))
    smoothing_passes: int = int(os.getenv("PLANNER_SMOOTHING_PASSES", "2"))


class TrajectoryOptimizer:
    """
    Practical Python trajectory smoothing inspired by B-spline optimization goals:
    reduce sharp second differences, then hard-validate collision and dynamics.
    """

    def __init__(self, config: TrajectoryConfig | None = None) -> None:
        self.config = config or TrajectoryConfig()
        self.last_optimize_time_ms = 0.0
        self.last_validation_reason = "not run"

    def optimize(self, raw_path: list[TrajectoryPoint], local_map: RollingLocalMap3D) -> list[TrajectoryPoint]:
        started = time.perf_counter()
        if len(raw_path) <= 2:
            self.last_optimize_time_ms = (time.perf_counter() - started) * 1000.0
            return raw_path

        positions = np.stack([point.position for point in raw_path], axis=0).astype(np.float32)
        times = np.array([point.time_s for point in raw_path], dtype=np.float32)

        dense_times = np.arange(float(times[0]), float(times[-1]) + self.config.sample_dt_s, self.config.sample_dt_s)
        dense = np.stack(
            [np.interp(dense_times, times, positions[:, axis]) for axis in range(3)],
            axis=1,
        ).astype(np.float32)

        smoothed = dense.copy()
        for _ in range(max(0, self.config.smoothing_passes)):
            if len(smoothed) > 2:
                smoothed[1:-1] = 0.25 * smoothed[:-2] + 0.5 * smoothed[1:-1] + 0.25 * smoothed[2:]

        trajectory = self._points_to_trajectory(smoothed, dense_times)
        if self.validate(trajectory, local_map):
            self.last_optimize_time_ms = (time.perf_counter() - started) * 1000.0
            return trajectory

        if self.validate(raw_path, local_map):
            self.last_optimize_time_ms = (time.perf_counter() - started) * 1000.0
            return raw_path

        self.last_optimize_time_ms = (time.perf_counter() - started) * 1000.0
        return []

    def _points_to_trajectory(self, positions: np.ndarray, times: np.ndarray) -> list[TrajectoryPoint]:
        if len(positions) == 0:
            return []

        velocities = np.zeros_like(positions)
        accelerations = np.zeros_like(positions)
        if len(positions) > 1:
            velocities = np.gradient(positions, times, axis=0).astype(np.float32)
        if len(positions) > 2:
            accelerations = np.gradient(velocities, times, axis=0).astype(np.float32)

        return [
            TrajectoryPoint(
                position=positions[i].astype(np.float32),
                velocity=velocities[i].astype(np.float32),
                acceleration=accelerations[i].astype(np.float32),
                time_s=float(times[i]),
            )
            for i in range(len(positions))
        ]

    def validate(self, trajectory: list[TrajectoryPoint], local_map: RollingLocalMap3D) -> bool:
        if not trajectory:
            self.last_validation_reason = "empty trajectory"
            return False

        if not local_map.trajectory_collision_free(trajectory):
            self.last_validation_reason = "collision"
            return False

        previous_accel = None
        previous_time = None
        for point in trajectory:
            horizontal_speed = float(np.linalg.norm(point.velocity[:2]))
            vertical_speed = abs(float(point.velocity[2]))
            accel = float(np.linalg.norm(point.acceleration))

            if horizontal_speed > self.config.max_horizontal_speed_m_s + 0.5:
                self.last_validation_reason = "horizontal speed"
                return False
            if vertical_speed > self.config.max_vertical_speed_m_s + 0.5:
                self.last_validation_reason = "vertical speed"
                return False
            if accel > self.config.max_accel_m_s2 * 3.0:
                self.last_validation_reason = "acceleration"
                return False

            if previous_accel is not None and previous_time is not None:
                dt = max(point.time_s - previous_time, 1e-6)
                jerk = float(np.linalg.norm(point.acceleration - previous_accel) / dt)
                if jerk > self.config.max_jerk_m_s3 * 10.0:
                    self.last_validation_reason = "jerk"
                    return False

            previous_accel = point.acceleration
            previous_time = point.time_s

        self.last_validation_reason = "valid"
        return True
