from __future__ import annotations

from dataclasses import dataclass
import json
import math
import os
from pathlib import Path
import time


def _env_bool(name: str, default: str = "true") -> bool:
    return os.getenv(name, default).strip().lower() in {"1", "true", "yes", "on"}


def _env_float(name: str, default: str) -> float:
    try:
        return float(os.getenv(name, default))
    except (TypeError, ValueError):
        return float(default)


def normalize_real_battery_percent(value: float | None) -> float | None:
    if value is None:
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    if math.isnan(result) or result < 0:
        return None
    if result <= 1.0:
        result *= 100.0
    return max(0.0, min(100.0, result))


def battery_level(percent: float | None) -> str | None:
    if percent is None:
        return None
    if percent < 10.0:
        return "VERY LOW"
    if percent < 20.0:
        return "CRITICAL"
    if percent < 30.0:
        return "LOW"
    return "NORMAL"


@dataclass(frozen=True)
class BatteryInputs:
    armed: bool | None = None
    in_air: bool | None = None
    velocity_north_m_s: float | None = None
    velocity_east_m_s: float | None = None
    velocity_down_m_s: float | None = None


@dataclass(frozen=True)
class BatterySnapshot:
    percent: float
    level: str
    drain_rate_pct_s: float
    horizontal_speed_m_s: float
    climb_speed_m_s: float


class SitlBatterySimulator:
    def __init__(self, state_path: str | None = None) -> None:
        self.enabled = _env_bool("SITL_BATTERY_SIM_ENABLED", os.getenv("USE_SITL_BATTERY_SIM", "true"))
        self.start_percent = _env_float("SITL_BATTERY_START_PERCENT", "100.0")
        self.min_percent = _env_float("SITL_BATTERY_MIN_PERCENT", "0.0")
        self.disarmed_drain = _env_float("SITL_BATTERY_DISARMED_DRAIN_PCT_S", "0.002")
        self.ground_drain = _env_float("SITL_BATTERY_GROUND_DRAIN_PCT_S", "0.005")
        self.hover_drain = _env_float("SITL_BATTERY_HOVER_DRAIN_PCT_S", "0.025")
        self.speed_extra_drain = _env_float("SITL_BATTERY_SPEED_EXTRA_DRAIN_PCT_S", "0.015")
        self.climb_extra_drain = _env_float("SITL_BATTERY_CLIMB_EXTRA_DRAIN_PCT_S", "0.020")
        self.reference_speed = max(_env_float("SITL_BATTERY_REFERENCE_SPEED_M_S", "10.0"), 0.1)
        self.reference_climb_speed = max(_env_float("SITL_BATTERY_REFERENCE_CLIMB_SPEED_M_S", "3.0"), 0.1)
        self.state_path = Path(
            state_path
            or os.getenv("SITL_BATTERY_STATE_FILE", "/tmp/forest3d_sitl_battery_state.json")
        )
        self._last_level: str | None = None

    def update(self, inputs: BatteryInputs) -> BatterySnapshot | None:
        if not self.enabled:
            return None

        now = time.monotonic()
        state = self._read_state()
        percent = float(state.get("percent", self.start_percent))
        previous_time = float(state.get("last_update_monotonic", now))
        dt = max(0.0, min(now - previous_time, 5.0))
        rate, horizontal_speed, climb_speed = self._drain_rate(inputs)
        percent = max(self.min_percent, percent - rate * dt)
        level = battery_level(percent) or "NORMAL"

        self._write_state(
            {
                "percent": percent,
                "last_update_monotonic": now,
                "level": level,
                "drain_rate_pct_s": rate,
                "horizontal_speed_m_s": horizontal_speed,
                "climb_speed_m_s": climb_speed,
            }
        )

        if level != self._last_level:
            self._last_level = level
            if level != "NORMAL":
                print(f"[BATTERY] {percent:.0f}% - {level}", flush=True)

        return BatterySnapshot(
            percent=round(percent, 2),
            level=level,
            drain_rate_pct_s=rate,
            horizontal_speed_m_s=horizontal_speed,
            climb_speed_m_s=climb_speed,
        )

    def snapshot(self) -> BatterySnapshot | None:
        if not self.enabled:
            return None

        state = self._read_state()
        percent = float(state.get("percent", self.start_percent))
        level = str(state.get("level") or battery_level(percent) or "NORMAL")
        return BatterySnapshot(
            percent=round(percent, 2),
            level=level,
            drain_rate_pct_s=float(state.get("drain_rate_pct_s", 0.0)),
            horizontal_speed_m_s=float(state.get("horizontal_speed_m_s", 0.0)),
            climb_speed_m_s=float(state.get("climb_speed_m_s", 0.0)),
        )

    def _drain_rate(self, inputs: BatteryInputs) -> tuple[float, float, float]:
        north = float(inputs.velocity_north_m_s or 0.0)
        east = float(inputs.velocity_east_m_s or 0.0)
        down = float(inputs.velocity_down_m_s or 0.0)
        horizontal_speed = math.hypot(north, east)
        climb_speed = max(0.0, -down)

        if not inputs.armed:
            base = self.disarmed_drain
        elif not inputs.in_air:
            base = self.ground_drain
        else:
            base = self.hover_drain

        speed_factor = max(0.0, min(horizontal_speed / self.reference_speed, 1.0))
        climb_factor = max(0.0, min(climb_speed / self.reference_climb_speed, 1.0))

        rate = (
            base
            + speed_factor * self.speed_extra_drain
            + climb_factor * self.climb_extra_drain
        )
        return rate, horizontal_speed, climb_speed

    def _read_state(self) -> dict:
        try:
            return json.loads(self.state_path.read_text(encoding="utf-8"))
        except (OSError, ValueError, json.JSONDecodeError):
            return {
                "percent": max(self.min_percent, min(100.0, self.start_percent)),
                "last_update_monotonic": time.monotonic(),
            }

    def _write_state(self, state: dict) -> None:
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = self.state_path.with_suffix(".tmp")
        tmp_path.write_text(json.dumps(state, separators=(",", ":")), encoding="utf-8")
        tmp_path.replace(self.state_path)
