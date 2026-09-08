from __future__ import annotations

import os
import time
from dataclasses import dataclass

import numpy as np

from .kinodynamic_astar import KinodynamicAStar
from .local_map_3d import LocalMapConfig, RollingLocalMap3D
from .planner_types import PlannerResult, PlannerState, SavedMotion, State3D, VelocityCommand
from .trajectory_optimizer import TrajectoryOptimizer
from .trajectory_tracker import TrajectoryTracker


@dataclass(frozen=True)
class LocalPlannerConfig:
    enabled: bool = os.getenv("LOCAL_PLANNER_ENABLED", "true").lower() == "true"
    horizon_m: float = float(os.getenv("PLANNER_HORIZON_M", "15.0"))
    execution_horizon_s: float = float(os.getenv("PLANNER_EXECUTION_HORIZON_S", "0.75"))
    emergency_distance_m: float = float(os.getenv("PLANNER_EMERGENCY_DISTANCE_M", "2.0"))
    emergency_corridor_m: float = float(os.getenv("PLANNER_EMERGENCY_CORRIDOR_M", "2.0"))
    min_clearance_m: float = float(os.getenv("PLANNER_MIN_CLEARANCE_M", "1.0"))
    max_replan_failures: int = int(os.getenv("PLANNER_MAX_REPLAN_FAILURES", "5"))
    perf_log_interval_s: float = float(os.getenv("PLANNER_PERF_LOG_INTERVAL_S", "2.0"))


class LocalPlanner3D:
    def __init__(self, config: LocalPlannerConfig | None = None) -> None:
        self.config = config or LocalPlannerConfig()
        self.map = RollingLocalMap3D(LocalMapConfig())
        self.search = KinodynamicAStar()
        self.optimizer = TrajectoryOptimizer()
        self.tracker = TrajectoryTracker()
        self.state = PlannerState.IDLE
        self.saved_motion: SavedMotion | None = None
        self.trajectory = []
        self.trajectory_started_at = 0.0
        self.replan_failures = 0
        self._last_perf_log = 0.0

    def is_active(self) -> bool:
        return self.state in {
            PlannerState.BRAKING,
            PlannerState.PLANNING,
            PlannerState.TRACKING,
            PlannerState.REPLANNING,
            PlannerState.REJOINING,
            PlannerState.RECOVERY,
            PlannerState.FAILSAFE,
        }

    def cancel(self) -> None:
        self.state = PlannerState.IDLE
        self.saved_motion = None
        self.trajectory = []
        self.replan_failures = 0

    def begin(self, saved_motion: SavedMotion) -> None:
        if self.saved_motion is None:
            self.saved_motion = saved_motion
        if self.state in (PlannerState.IDLE, PlannerState.MONITORING):
            self.state = PlannerState.PLANNING

    def update(
        self,
        points_body: np.ndarray,
        yaw_deg: float,
        current_velocity_body: np.ndarray | None = None,
    ) -> PlannerResult:
        started = time.perf_counter()
        if not self.config.enabled:
            return self._result(PlannerState.IDLE, None, "disabled", started)

        map_started = time.perf_counter()
        self.map.update_from_points(points_body)
        map_ms = (time.perf_counter() - map_started) * 1000.0

        if self.saved_motion is None:
            self.state = PlannerState.MONITORING
            return self._result(self.state, None, "no saved motion", started)

        if self._front_emergency_obstacle():
            self.state = PlannerState.FAILSAFE
            return self._result(self.state, VelocityCommand(0.0, 0.0, 0.0, yaw_deg), "emergency obstacle", started)

        velocity = current_velocity_body
        if velocity is None:
            velocity = np.array(
                [self.saved_motion.forward_m_s, self.saved_motion.right_m_s, -self.saved_motion.down_m_s],
                dtype=np.float32,
            )

        goal = self._local_goal()
        straight_safe = self.map.segment_collision_free(np.zeros(3, dtype=np.float32), goal)
        if straight_safe and not self.trajectory:
            self.state = PlannerState.MONITORING
            return self._result(
                self.state,
                VelocityCommand(
                    self.saved_motion.north_m_s,
                    self.saved_motion.east_m_s,
                    self.saved_motion.down_m_s,
                    self.saved_motion.yaw_deg,
                ),
                "straight path clear",
                started,
            )

        if not self.trajectory or not self.map.trajectory_collision_free(self.trajectory):
            self.state = PlannerState.PLANNING if not self.trajectory else PlannerState.REPLANNING
            search_started = time.perf_counter()
            raw_path = self.search.search(
                State3D(position=np.zeros(3, dtype=np.float32), velocity=velocity.astype(np.float32)),
                goal,
                self.map,
            )
            search_ms = (time.perf_counter() - search_started) * 1000.0

            opt_started = time.perf_counter()
            trajectory = self.optimizer.optimize(raw_path, self.map)
            opt_ms = (time.perf_counter() - opt_started) * 1000.0

            if not trajectory:
                self.replan_failures += 1
                if self.replan_failures >= self.config.max_replan_failures:
                    self.state = PlannerState.FAILSAFE
                    return self._result(self.state, VelocityCommand(0.0, 0.0, 0.0, yaw_deg), "no safe trajectory", started)
                self.state = PlannerState.RECOVERY
                return self._result(self.state, VelocityCommand(0.0, 0.0, 0.0, yaw_deg), "replan failed", started)

            self.trajectory = trajectory
            self.trajectory_started_at = time.monotonic()
            self.replan_failures = 0
            self.state = PlannerState.TRACKING
            self._log_perf(started, map_ms, search_ms, opt_ms)

        elapsed = time.monotonic() - self.trajectory_started_at
        command = self.tracker.command_at(self.trajectory, elapsed, yaw_deg)
        if command is None:
            self.state = PlannerState.FAILSAFE
            return self._result(self.state, VelocityCommand(0.0, 0.0, 0.0, yaw_deg), "tracker unavailable", started)

        if elapsed >= min(self.config.execution_horizon_s, self.trajectory[-1].time_s):
            self.trajectory = []
            self.state = PlannerState.REPLANNING

        if self._original_route_clear():
            self.state = PlannerState.REJOINING
            restored = self.saved_motion
            self.cancel()
            return self._result(
                PlannerState.REJOINING,
                VelocityCommand(restored.north_m_s, restored.east_m_s, restored.down_m_s, restored.yaw_deg),
                "original route restored",
                started,
            )

        return self._result(self.state, command, "tracking", started)

    def _local_goal(self) -> np.ndarray:
        assert self.saved_motion is not None
        direction = np.array(
            [self.saved_motion.forward_m_s, self.saved_motion.right_m_s, -self.saved_motion.down_m_s],
            dtype=np.float32,
        )
        norm = float(np.linalg.norm(direction))
        if norm < 1e-6:
            direction = np.array([1.0, 0.0, 0.0], dtype=np.float32)
        else:
            direction = direction / norm
        return direction * self.config.horizon_m

    def _original_route_clear(self) -> bool:
        if self.saved_motion is None:
            return False
        goal = self._local_goal()
        return self.map.segment_collision_free(np.zeros(3, dtype=np.float32), goal)

    def _front_emergency_obstacle(self) -> bool:
        centers = self.map.occupied_centers(inflated=False)
        if centers.size == 0:
            return False

        forward = centers[:, 0]
        side = np.abs(centers[:, 1])
        vertical = centers[:, 2]
        mask = (
            (forward >= 0.0)
            & (forward <= self.config.emergency_distance_m)
            & (side <= self.config.emergency_corridor_m)
            & (vertical >= -0.2)
        )
        return bool(np.any(mask))

    def _result(
        self,
        state: PlannerState,
        command: VelocityCommand | None,
        reason: str,
        started: float,
    ) -> PlannerResult:
        clearance = self.map.clearance(np.zeros(3, dtype=np.float32))
        return PlannerResult(
            state=state,
            command=command,
            trajectory=list(self.trajectory),
            reason=reason,
            clearance_m=clearance,
            planning_time_ms=(time.perf_counter() - started) * 1000.0,
        )

    def _log_perf(self, started: float, map_ms: float, search_ms: float, opt_ms: float) -> None:
        now = time.monotonic()
        if now - self._last_perf_log < self.config.perf_log_interval_s:
            return
        self._last_perf_log = now
        total_ms = (time.perf_counter() - started) * 1000.0
        print(
            "[PLANNER-PERF] "
            f"map={map_ms:.1f}ms search={search_ms:.1f}ms "
            f"optimize={opt_ms:.1f}ms total={total_ms:.1f}ms",
            flush=True,
        )
