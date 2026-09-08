from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

import numpy as np


class PlannerState(Enum):
    IDLE = "IDLE"
    MONITORING = "MONITORING"
    BRAKING = "BRAKING"
    PLANNING = "PLANNING"
    TRACKING = "TRACKING"
    REPLANNING = "REPLANNING"
    REJOINING = "REJOINING"
    RECOVERY = "RECOVERY"
    FAILSAFE = "FAILSAFE"


class MotionOwner(Enum):
    MANUAL = "MANUAL"
    PLANNER = "PLANNER"
    EMERGENCY = "EMERGENCY"


@dataclass(frozen=True)
class SavedMotion:
    forward_m_s: float
    right_m_s: float
    down_m_s: float
    north_m_s: float
    east_m_s: float
    yaw_deg: float


@dataclass(frozen=True)
class State3D:
    position: np.ndarray
    velocity: np.ndarray


@dataclass(frozen=True)
class TrajectoryPoint:
    position: np.ndarray
    velocity: np.ndarray
    acceleration: np.ndarray
    time_s: float


@dataclass(frozen=True)
class VelocityCommand:
    north_m_s: float
    east_m_s: float
    down_m_s: float
    yaw_deg: float


@dataclass(frozen=True)
class PlannerResult:
    state: PlannerState
    command: VelocityCommand | None
    trajectory: list[TrajectoryPoint]
    reason: str
    clearance_m: float
    planning_time_ms: float
