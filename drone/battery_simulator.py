from __future__ import annotations

from dataclasses import dataclass
import math
import time
from typing import Callable


LANDED_DRAIN_PERCENT_PER_MIN = 0.02
IDLE_DRAIN_PERCENT_PER_MIN = 0.03
HOVER_DRAIN_PERCENT_PER_MIN = 0.8
CRUISE_DRAIN_PERCENT_PER_MIN = 1.0
ASCEND_DRAIN_PERCENT_PER_MIN = 1.2
DESCEND_DRAIN_PERCENT_PER_MIN = 0.6

HORIZONTAL_SPEED_THRESHOLD_M_S = 0.3
VERTICAL_SPEED_THRESHOLD_M_S = 0.2

DRAIN_RATES_PERCENT_PER_MIN = {
    "LANDED": LANDED_DRAIN_PERCENT_PER_MIN,
    "IDLE": IDLE_DRAIN_PERCENT_PER_MIN,
    "HOVER": HOVER_DRAIN_PERCENT_PER_MIN,
    "CRUISE": CRUISE_DRAIN_PERCENT_PER_MIN,
    "ASCEND": ASCEND_DRAIN_PERCENT_PER_MIN,
    "DESCEND": DESCEND_DRAIN_PERCENT_PER_MIN,
}


def clamp_percent(value: float) -> float:
    if not math.isfinite(value):
        return 100.0
    return max(0.0, min(100.0, value))


def battery_state(percent: float) -> str:
    if percent <= 10.0:
        return "EMERGENCY"
    if percent <= 20.0:
        return "CRITICAL"
    if percent <= 30.0:
        return "LOW"
    return "NORMAL"


def preflight_battery_check(percent: float) -> tuple[str, str]:
    clamped = clamp_percent(percent)
    if clamped > 30.0:
        return "PASS", f"{clamped:.1f}% sufficient for operation"
    if clamped > 20.0:
        return "WARN", f"{clamped:.1f}% low battery"
    return "FAIL", f"{clamped:.1f}% too low for safe mission start"


def detect_battery_mode(
        *,
        in_air: bool,
        velocity_north_m_s: float = 0.0,
        velocity_east_m_s: float = 0.0,
        velocity_down_m_s: float = 0.0,
) -> str:
    if not in_air:
        return "LANDED"

    vertical_down = velocity_down_m_s if math.isfinite(velocity_down_m_s) else 0.0
    if vertical_down < -VERTICAL_SPEED_THRESHOLD_M_S:
        return "ASCEND"
    if vertical_down > VERTICAL_SPEED_THRESHOLD_M_S:
        return "DESCEND"

    north = velocity_north_m_s if math.isfinite(velocity_north_m_s) else 0.0
    east = velocity_east_m_s if math.isfinite(velocity_east_m_s) else 0.0
    horizontal_speed = math.hypot(north, east)
    if horizontal_speed > HORIZONTAL_SPEED_THRESHOLD_M_S:
        return "CRUISE"

    return "HOVER"


@dataclass
class BatterySnapshot:
    battery_percent: float
    battery_state: str
    battery_drain_mode: str
    drain_rate_percent_per_minute: float


class BatterySimulator:
    def __init__(
            self,
            initial_percent: float = 100.0,
            *,
            clock: Callable[[], float] | None = None,
    ) -> None:
        self._clock = clock or time.monotonic
        self._battery_percent = clamp_percent(float(initial_percent))
        self._mode = "LANDED"
        self._state = battery_state(self._battery_percent)
        self._last_update_s = self._clock()
        print(f"[BATTERY] Initialized: {self._battery_percent:.1f}%", flush=True)

    @property
    def percent(self) -> float:
        return self._battery_percent

    @property
    def mode(self) -> str:
        return self._mode

    @property
    def state(self) -> str:
        return self._state

    def update(self, mode: str | None = None) -> BatterySnapshot:
        now_s = self._clock()
        elapsed_s = max(0.0, now_s - self._last_update_s)
        self._last_update_s = now_s

        selected_mode = mode if mode in DRAIN_RATES_PERCENT_PER_MIN else "LANDED"
        if selected_mode != self._mode:
            print(f"[BATTERY] Mode changed: {self._mode} -> {selected_mode}", flush=True)
            self._mode = selected_mode

        drain_rate = DRAIN_RATES_PERCENT_PER_MIN[self._mode]
        self._battery_percent = clamp_percent(
            self._battery_percent - drain_rate * (elapsed_s / 60.0)
        )

        next_state = battery_state(self._battery_percent)
        if next_state != self._state:
            print(
                f"[BATTERY] State changed: {self._state} -> {next_state} "
                f"({self._battery_percent:.1f}%)",
                flush=True,
            )
            self._state = next_state

        return self.snapshot()

    def snapshot(self) -> BatterySnapshot:
        return BatterySnapshot(
            battery_percent=self._battery_percent,
            battery_state=self._state,
            battery_drain_mode=self._mode,
            drain_rate_percent_per_minute=DRAIN_RATES_PERCENT_PER_MIN[self._mode],
        )
