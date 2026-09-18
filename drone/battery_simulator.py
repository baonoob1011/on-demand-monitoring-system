from __future__ import annotations

from dataclasses import dataclass
import math
import os
import time
from typing import Callable


LANDED_CURRENT_A = float(os.getenv("SIM_BATTERY_LANDED_CURRENT_A", "0.05"))
ARMED_IDLE_CURRENT_A = float(os.getenv("SIM_BATTERY_ARMED_IDLE_CURRENT_A", "0.5"))
HOVER_CURRENT_A = float(os.getenv("SIM_BATTERY_HOVER_CURRENT_A", "4.0"))
CRUISE_CURRENT_A = float(os.getenv("SIM_BATTERY_CRUISE_CURRENT_A", "5.5"))
ASCEND_CURRENT_A = float(os.getenv("SIM_BATTERY_ASCEND_CURRENT_A", "7.5"))
DESCEND_CURRENT_A = float(os.getenv("SIM_BATTERY_DESCEND_CURRENT_A", "3.0"))

HORIZONTAL_SPEED_THRESHOLD_M_S = float(os.getenv("SIM_BATTERY_HORIZONTAL_SPEED_THRESHOLD_M_S", "0.5"))
VERTICAL_SPEED_THRESHOLD_M_S = float(os.getenv("SIM_BATTERY_VERTICAL_SPEED_THRESHOLD_M_S", "0.5"))
MODE_STABILIZATION_SECONDS = float(os.getenv("SIM_BATTERY_MODE_STABILIZATION_SECONDS", "0.75"))

CURRENT_DRAW_A = {
    "LANDED": LANDED_CURRENT_A,
    "ARMED_IDLE": ARMED_IDLE_CURRENT_A,
    "HOVER": HOVER_CURRENT_A,
    "CRUISE": CRUISE_CURRENT_A,
    "ASCEND": ASCEND_CURRENT_A,
    "DESCEND": DESCEND_CURRENT_A,
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
        armed: bool = True,
        in_air: bool,
        velocity_north_m_s: float = 0.0,
        velocity_east_m_s: float = 0.0,
        velocity_down_m_s: float = 0.0,
        flight_mode: str | None = None,
) -> str:
    normalized_flight_mode = (flight_mode or "").strip().upper()
    if "TAKEOFF" in normalized_flight_mode:
        return "ASCEND"
    if "LAND" in normalized_flight_mode:
        return "DESCEND"

    if not armed:
        return "LANDED"
    if not in_air:
        return "ARMED_IDLE"

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
    current_draw_a: float
    capacity_mAh: float
    remaining_mAh: float
    consumed_mAh: float

    @property
    def drain_rate_percent_per_minute(self) -> float:
        if self.capacity_mAh <= 0:
            return 0.0
        return (self.current_draw_a * 1000.0 / 60.0 / self.capacity_mAh) * 100.0


class BatterySimulator:
    def __init__(
            self,
            initial_percent: float = 100.0,
            *,
            capacity_mAh: float = 5000.0,
            current_draw_a: dict[str, float] | None = None,
            clock: Callable[[], float] | None = None,
    ) -> None:
        self._clock = clock or time.monotonic
        self._capacity_mAh = max(1.0, float(capacity_mAh))
        self._current_draw_a = dict(CURRENT_DRAW_A)
        if current_draw_a:
            self._current_draw_a.update({
                str(key).strip().upper(): max(0.0, float(value))
                for key, value in current_draw_a.items()
            })
        initial = clamp_percent(float(initial_percent))
        self._consumed_mAh = self._capacity_mAh * (1.0 - initial / 100.0)
        self._mode = "LANDED"
        self._pending_mode: str | None = None
        self._pending_mode_since_s: float | None = None
        self._state = battery_state(initial)
        self._last_update_s = self._clock()
        print(
            f"[BATTERY] Initialized: {self._capacity_mAh:.0f}mAh, {initial:.1f}%",
            flush=True,
        )

    @property
    def percent(self) -> float:
        return self.get_percent()

    @property
    def mode(self) -> str:
        return self._mode

    @property
    def state(self) -> str:
        return self._state

    @property
    def capacity_mAh(self) -> float:
        return self._capacity_mAh

    def _remaining_mAh(self) -> float:
        return max(0.0, self._capacity_mAh - self._consumed_mAh)

    def _percent_from_charge(self) -> float:
        return clamp_percent((self._remaining_mAh() / self._capacity_mAh) * 100.0)

    def _current_for_mode(self, mode: str) -> float:
        return self._current_draw_a.get(mode, self._current_draw_a["LANDED"])

    def update(
            self,
            mode: str | None = None,
            *,
            armed: bool | None = None,
            in_air: bool | None = None,
            velocity_north_m_s: float = 0.0,
            velocity_east_m_s: float = 0.0,
            velocity_down_m_s: float = 0.0,
            flight_mode: str | None = None,
    ) -> BatterySnapshot:
        now_s = self._clock()
        elapsed_s = max(0.0, now_s - self._last_update_s)
        self._last_update_s = now_s

        if mode is None:
            selected_mode = detect_battery_mode(
                armed=bool(armed),
                in_air=bool(in_air),
                velocity_north_m_s=velocity_north_m_s,
                velocity_east_m_s=velocity_east_m_s,
                velocity_down_m_s=velocity_down_m_s,
                flight_mode=flight_mode,
            )
        else:
            selected_mode = mode.strip().upper()
            if selected_mode == "IDLE":
                selected_mode = "ARMED_IDLE"
            if selected_mode not in self._current_draw_a:
                selected_mode = "LANDED"

        if mode is None:
            self._apply_detected_mode(
                selected_mode,
                now_s,
                velocity_north_m_s,
                velocity_east_m_s,
                velocity_down_m_s,
            )
        else:
            self._set_mode(
                selected_mode,
                velocity_north_m_s,
                velocity_east_m_s,
                velocity_down_m_s,
            )

        current_a = self._current_for_mode(self._mode)
        consumed_delta_mAh = current_a * 1000.0 * elapsed_s / 3600.0
        self._consumed_mAh = min(self._capacity_mAh, max(0.0, self._consumed_mAh + consumed_delta_mAh))

        percent = self._percent_from_charge()
        next_state = battery_state(percent)
        if next_state != self._state:
            print(f"[BATTERY] State: {self._state} -> {next_state} ({percent:.1f}%)", flush=True)
            self._state = next_state

        return self.snapshot()

    def _apply_detected_mode(
            self,
            selected_mode: str,
            now_s: float,
            velocity_north_m_s: float,
            velocity_east_m_s: float,
            velocity_down_m_s: float,
    ) -> None:
        if selected_mode == self._mode:
            self._pending_mode = None
            self._pending_mode_since_s = None
            return

        if selected_mode in {"LANDED", "ARMED_IDLE", "HOVER"}:
            self._set_mode(
                selected_mode,
                velocity_north_m_s,
                velocity_east_m_s,
                velocity_down_m_s,
            )
            return

        if selected_mode != self._pending_mode:
            self._pending_mode = selected_mode
            self._pending_mode_since_s = now_s
            return

        if (
                self._pending_mode_since_s is not None
                and now_s - self._pending_mode_since_s >= MODE_STABILIZATION_SECONDS
        ):
            self._set_mode(
                selected_mode,
                velocity_north_m_s,
                velocity_east_m_s,
                velocity_down_m_s,
            )

    def _set_mode(
            self,
            selected_mode: str,
            velocity_north_m_s: float,
            velocity_east_m_s: float,
            velocity_down_m_s: float,
    ) -> None:
        if selected_mode == self._mode:
            return

        previous_mode = self._mode
        self._mode = selected_mode
        self._pending_mode = None
        self._pending_mode_since_s = None
        print(
            f"[BATTERY] {previous_mode} -> {selected_mode} | "
            f"N={velocity_north_m_s:.2f} E={velocity_east_m_s:.2f} D={velocity_down_m_s:.2f} m/s | "
            f"current={self._current_for_mode(selected_mode):.1f}A",
            flush=True,
        )

    def snapshot(self) -> BatterySnapshot:
        percent = self._percent_from_charge()
        return BatterySnapshot(
            battery_percent=percent,
            battery_state=self._state,
            battery_drain_mode=self._mode,
            current_draw_a=self._current_for_mode(self._mode),
            capacity_mAh=self._capacity_mAh,
            remaining_mAh=self._remaining_mAh(),
            consumed_mAh=self._consumed_mAh,
        )

    def get_percent(self) -> float:
        return self.snapshot().battery_percent

    def get_remaining_mah(self) -> float:
        return self.snapshot().remaining_mAh

    def get_consumed_mah(self) -> float:
        return self.snapshot().consumed_mAh

    def get_current_draw_a(self) -> float:
        return self.snapshot().current_draw_a

    def get_drain_mode(self) -> str:
        return self._mode

    def get_state(self) -> str:
        return self._state
