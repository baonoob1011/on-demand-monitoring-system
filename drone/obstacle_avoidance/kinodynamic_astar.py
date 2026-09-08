from __future__ import annotations

import heapq
import math
import os
import time
from dataclasses import dataclass

import numpy as np

from .local_map_3d import RollingLocalMap3D
from .planner_types import State3D, TrajectoryPoint


@dataclass(frozen=True)
class KinodynamicConfig:
    max_horizontal_speed_m_s: float = float(os.getenv("PLANNER_MAX_HORIZONTAL_SPEED_M_S", "3.0"))
    max_vertical_speed_m_s: float = float(os.getenv("PLANNER_MAX_VERTICAL_SPEED_M_S", "1.5"))
    max_accel_m_s2: float = float(os.getenv("PLANNER_MAX_ACCEL_M_S2", "2.0"))
    primitive_duration_s: float = float(os.getenv("PLANNER_PRIMITIVE_DURATION_S", "0.5"))
    collision_dt_s: float = float(os.getenv("PLANNER_COLLISION_DT_S", "0.1"))
    horizon_m: float = float(os.getenv("PLANNER_HORIZON_M", "15.0"))
    goal_tolerance_m: float = float(os.getenv("PLANNER_GOAL_TOLERANCE_M", "1.0"))
    state_resolution_m: float = float(os.getenv("PLANNER_STATE_RESOLUTION_M", "0.5"))
    velocity_resolution_m_s: float = float(os.getenv("PLANNER_VELOCITY_RESOLUTION_M_S", "0.5"))
    max_search_time_ms: float = float(os.getenv("PLANNER_MAX_SEARCH_TIME_MS", "150"))
    max_search_nodes: int = int(os.getenv("PLANNER_MAX_SEARCH_NODES", "20000"))


@dataclass
class _Node:
    position: np.ndarray
    velocity: np.ndarray
    g_score: float
    f_score: float
    parent: int | None
    acceleration: np.ndarray
    time_s: float


class KinodynamicAStar:
    """
    Compact Python kinodynamic A* inspired by Fast-Planner's state-space search.
    State is [position, velocity], controls are bounded accelerations.
    """

    def __init__(self, config: KinodynamicConfig | None = None) -> None:
        self.config = config or KinodynamicConfig()
        a = self.config.max_accel_m_s2
        self._accelerations = [
            np.array(v, dtype=np.float32)
            for v in (
                (0, 0, 0),
                (a, 0, 0),
                (-a, 0, 0),
                (0, a, 0),
                (0, -a, 0),
                (0, 0, a),
                (0, 0, -a),
                (a, a, 0),
                (a, -a, 0),
                (a, 0, a),
                (a, 0, -a),
                (0, a, a),
                (0, -a, a),
            )
        ]
        self.last_nodes_expanded = 0
        self.last_search_time_ms = 0.0

    def _heuristic(self, position: np.ndarray, velocity: np.ndarray, goal: np.ndarray) -> float:
        distance = float(np.linalg.norm(goal - position))
        speed = max(self.config.max_horizontal_speed_m_s, 0.1)
        direction = goal - position
        norm = float(np.linalg.norm(direction))
        alignment_penalty = 0.0
        if norm > 1e-6:
            desired = direction / norm
            vel_norm = float(np.linalg.norm(velocity))
            if vel_norm > 1e-6:
                alignment_penalty = max(0.0, 1.0 - float(np.dot(velocity / vel_norm, desired)))
        return distance / speed + alignment_penalty

    def _state_key(self, position: np.ndarray, velocity: np.ndarray) -> tuple[int, int, int, int, int, int]:
        rp = self.config.state_resolution_m
        rv = self.config.velocity_resolution_m_s
        p = np.round(position / rp).astype(int)
        v = np.round(velocity / rv).astype(int)
        return (int(p[0]), int(p[1]), int(p[2]), int(v[0]), int(v[1]), int(v[2]))

    def _velocity_valid(self, velocity: np.ndarray) -> bool:
        horizontal = float(np.linalg.norm(velocity[:2]))
        vertical = abs(float(velocity[2]))
        return (
            horizontal <= self.config.max_horizontal_speed_m_s + 1e-6
            and vertical <= self.config.max_vertical_speed_m_s + 1e-6
        )

    def _in_horizon(self, position: np.ndarray, start: np.ndarray) -> bool:
        return float(np.linalg.norm(position - start)) <= self.config.horizon_m

    def _primitive_collision_free(
        self,
        local_map: RollingLocalMap3D,
        position: np.ndarray,
        velocity: np.ndarray,
        acceleration: np.ndarray,
        duration_s: float,
    ) -> bool:
        previous = position
        steps = max(2, int(math.ceil(duration_s / self.config.collision_dt_s)) + 1)
        for t in np.linspace(0.0, duration_s, steps)[1:]:
            sample = position + velocity * t + 0.5 * acceleration * (t * t)
            if not local_map.segment_collision_free(previous, sample):
                return False
            previous = sample
        return True

    def search(self, start: State3D, goal: np.ndarray, local_map: RollingLocalMap3D) -> list[TrajectoryPoint]:
        started = time.perf_counter()
        start_pos = np.asarray(start.position, dtype=np.float32)
        goal = np.asarray(goal, dtype=np.float32)
        start_vel = np.asarray(start.velocity, dtype=np.float32)

        first = _Node(
            position=start_pos,
            velocity=start_vel,
            g_score=0.0,
            f_score=self._heuristic(start_pos, start_vel, goal),
            parent=None,
            acceleration=np.zeros(3, dtype=np.float32),
            time_s=0.0,
        )
        nodes = [first]
        open_heap = [(first.f_score, 0)]
        best_g = {self._state_key(first.position, first.velocity): 0.0}

        self.last_nodes_expanded = 0
        best_index = 0
        best_distance = float(np.linalg.norm(goal - first.position))

        while open_heap:
            elapsed_ms = (time.perf_counter() - started) * 1000.0
            if elapsed_ms > self.config.max_search_time_ms:
                break
            if self.last_nodes_expanded >= self.config.max_search_nodes:
                break

            _f_score, index = heapq.heappop(open_heap)
            node = nodes[index]
            self.last_nodes_expanded += 1

            distance_to_goal = float(np.linalg.norm(goal - node.position))
            if distance_to_goal < best_distance:
                best_distance = distance_to_goal
                best_index = index
            if distance_to_goal <= self.config.goal_tolerance_m:
                self.last_search_time_ms = (time.perf_counter() - started) * 1000.0
                return self._reconstruct(nodes, index)

            for acceleration in self._accelerations:
                dt = self.config.primitive_duration_s
                next_velocity = node.velocity + acceleration * dt
                if not self._velocity_valid(next_velocity):
                    continue

                next_position = node.position + node.velocity * dt + 0.5 * acceleration * dt * dt
                if not self._in_horizon(next_position, start_pos):
                    continue
                if not self._primitive_collision_free(local_map, node.position, node.velocity, acceleration, dt):
                    continue

                key = self._state_key(next_position, next_velocity)
                move_cost = dt + 0.05 * float(np.linalg.norm(acceleration))
                g_score = node.g_score + move_cost
                if g_score >= best_g.get(key, float("inf")):
                    continue

                best_g[key] = g_score
                child = _Node(
                    position=next_position.astype(np.float32),
                    velocity=next_velocity.astype(np.float32),
                    g_score=g_score,
                    f_score=g_score + self._heuristic(next_position, next_velocity, goal),
                    parent=index,
                    acceleration=acceleration,
                    time_s=node.time_s + dt,
                )
                nodes.append(child)
                heapq.heappush(open_heap, (child.f_score, len(nodes) - 1))

        self.last_search_time_ms = (time.perf_counter() - started) * 1000.0
        if best_index != 0 and best_distance < float(np.linalg.norm(goal - start_pos)):
            return self._reconstruct(nodes, best_index)
        return []

    def _reconstruct(self, nodes: list[_Node], index: int) -> list[TrajectoryPoint]:
        path = []
        while index is not None:
            node = nodes[index]
            path.append(
                TrajectoryPoint(
                    position=node.position.copy(),
                    velocity=node.velocity.copy(),
                    acceleration=node.acceleration.copy(),
                    time_s=node.time_s,
                )
            )
            index = node.parent
        path.reverse()
        return path
