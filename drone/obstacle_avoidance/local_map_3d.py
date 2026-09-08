from __future__ import annotations

import math
import os
from dataclasses import dataclass
from itertools import product

import numpy as np


@dataclass(frozen=True)
class LocalMapConfig:
    local_radius_m: float = float(os.getenv("PLANNER_LOCAL_RADIUS_M", "25.0"))
    local_vertical_m: float = float(os.getenv("PLANNER_LOCAL_VERTICAL_M", "15.0"))
    voxel_size_m: float = float(os.getenv("PLANNER_VOXEL_SIZE_M", "0.5"))
    drone_radius_m: float = float(os.getenv("PLANNER_DRONE_RADIUS_M", "0.45"))
    drone_half_height_m: float = float(os.getenv("PLANNER_DRONE_HALF_HEIGHT_M", "0.25"))
    safety_margin_m: float = float(os.getenv("PLANNER_SAFETY_MARGIN_M", "0.75"))
    ground_ignore_below_m: float = float(os.getenv("PLANNER_GROUND_IGNORE_BELOW_M", "0.00"))
    unknown_is_occupied: bool = os.getenv("PLANNER_UNKNOWN_IS_OCCUPIED", "false").lower() == "true"


class RollingLocalMap3D:
    """
    Sparse local occupancy map in planner BODY coordinates:
        X forward, Y right, Z up.
    The map is rebuilt from each fresh point cloud during this migration phase.
    """

    def __init__(self, config: LocalMapConfig | None = None) -> None:
        self.config = config or LocalMapConfig()
        self.occupied: set[tuple[int, int, int]] = set()
        self.inflated: set[tuple[int, int, int]] = set()
        self.last_point_count = 0

    @property
    def collision_radius_m(self) -> float:
        return self.config.drone_radius_m + self.config.safety_margin_m

    def clear(self) -> None:
        self.occupied.clear()
        self.inflated.clear()
        self.last_point_count = 0

    def world_to_voxel(self, point: np.ndarray | tuple[float, float, float]) -> tuple[int, int, int]:
        x, y, z = point
        r = self.config.voxel_size_m
        return (math.floor(float(x) / r), math.floor(float(y) / r), math.floor(float(z) / r))

    def voxel_to_world(self, voxel: tuple[int, int, int]) -> np.ndarray:
        r = self.config.voxel_size_m
        return np.array([(voxel[0] + 0.5) * r, (voxel[1] + 0.5) * r, (voxel[2] + 0.5) * r], dtype=np.float32)

    def in_bounds(self, point: np.ndarray | tuple[float, float, float]) -> bool:
        x, y, z = point
        return (
            -self.config.local_radius_m <= float(x) <= self.config.local_radius_m
            and -self.config.local_radius_m <= float(y) <= self.config.local_radius_m
            and -self.config.local_vertical_m <= float(z) <= self.config.local_vertical_m
        )

    def update_from_points(self, points: np.ndarray) -> None:
        self.clear()
        if points.size == 0:
            return

        pts = np.asarray(points, dtype=np.float32).reshape((-1, 3))
        finite = np.isfinite(pts).all(axis=1)
        in_bounds = (
            (np.abs(pts[:, 0]) <= self.config.local_radius_m)
            & (np.abs(pts[:, 1]) <= self.config.local_radius_m)
            & (np.abs(pts[:, 2]) <= self.config.local_vertical_m)
            & (pts[:, 2] >= self.config.ground_ignore_below_m)
        )
        pts = pts[finite & in_bounds]
        self.last_point_count = int(len(pts))

        for point in pts:
            self.occupied.add(self.world_to_voxel(point))

        self._inflate()

    def _inflate(self) -> None:
        r = self.config.voxel_size_m
        horizontal_steps = math.ceil(self.collision_radius_m / r)
        vertical_steps = math.ceil((self.config.drone_half_height_m + self.config.safety_margin_m) / r)

        for voxel in self.occupied:
            vx, vy, vz = voxel
            for dx, dy, dz in product(
                range(-horizontal_steps, horizontal_steps + 1),
                range(-horizontal_steps, horizontal_steps + 1),
                range(-vertical_steps, vertical_steps + 1),
            ):
                if math.hypot(dx * r, dy * r) <= self.collision_radius_m + r * 0.5:
                    self.inflated.add((vx + dx, vy + dy, vz + dz))

    def is_occupied(self, point: np.ndarray | tuple[float, float, float], inflated: bool = True) -> bool:
        if not self.in_bounds(point):
            return self.config.unknown_is_occupied
        voxels = self.inflated if inflated else self.occupied
        return self.world_to_voxel(point) in voxels

    def is_free(self, point: np.ndarray | tuple[float, float, float]) -> bool:
        return not self.is_occupied(point, inflated=True)

    def occupied_centers(self, inflated: bool = False) -> np.ndarray:
        voxels = self.inflated if inflated else self.occupied
        if not voxels:
            return np.empty((0, 3), dtype=np.float32)
        return np.stack([self.voxel_to_world(v) for v in voxels], axis=0)

    def nearby_occupied(self, point: np.ndarray, radius_m: float) -> np.ndarray:
        centers = self.occupied_centers(inflated=False)
        if centers.size == 0:
            return centers
        distances = np.linalg.norm(centers - point.reshape(1, 3), axis=1)
        return centers[distances <= radius_m]

    def nearest_obstacle_distance(self, point: np.ndarray) -> float:
        centers = self.occupied_centers(inflated=False)
        if centers.size == 0:
            return float("inf")
        return float(np.linalg.norm(centers - point.reshape(1, 3), axis=1).min())

    def clearance(self, point: np.ndarray) -> float:
        return self.nearest_obstacle_distance(point) - self.collision_radius_m

    def segment_collision_free(self, start: np.ndarray, end: np.ndarray, step_m: float | None = None) -> bool:
        step = step_m or self.config.voxel_size_m * 0.5
        start = np.asarray(start, dtype=np.float32)
        end = np.asarray(end, dtype=np.float32)
        distance = float(np.linalg.norm(end - start))
        samples = max(2, int(math.ceil(distance / step)) + 1)
        for alpha in np.linspace(0.0, 1.0, samples):
            if self.is_occupied(start + (end - start) * alpha):
                return False
        return True

    def trajectory_collision_free(self, trajectory) -> bool:
        if not trajectory:
            return True
        previous = trajectory[0].position
        if self.is_occupied(previous):
            return False
        for point in trajectory[1:]:
            if not self.segment_collision_free(previous, point.position):
                return False
            previous = point.position
        return True
