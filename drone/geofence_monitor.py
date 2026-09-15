from __future__ import annotations

import asyncio
import hashlib
import json
import math
import os
import time
from dataclasses import dataclass
from enum import Enum
from typing import Any, Callable, Iterable

import httpx


RESTRICTED_ZONE_TYPES = {"AIRPORT", "RESTRICTED", "NO_FLY", "NO-FLY", "NOFLY"}
DEFAULT_PX4_HOME_SIM_X_M = 0.0
DEFAULT_PX4_HOME_SIM_Y_M = -280.0


def _float_env(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        value = float(raw)
    except ValueError:
        return default
    return value if math.isfinite(value) else default


PX4_HOME_SIM_X_M = _float_env("GEOFENCE_PX4_HOME_SIM_X_M", DEFAULT_PX4_HOME_SIM_X_M)
PX4_HOME_SIM_Y_M = _float_env("GEOFENCE_PX4_HOME_SIM_Y_M", DEFAULT_PX4_HOME_SIM_Y_M)


class GeofenceLevel(Enum):
    UNKNOWN = "UNKNOWN"
    SAFE = "SAFE"
    CAUTION = "CAUTION"
    DANGER = "DANGER"
    VIOLATION = "VIOLATION"


@dataclass(frozen=True)
class RestrictedZone:
    id: str
    code: str
    name: str
    zone_type: str
    coordinates: tuple[tuple[float, float], ...]


@dataclass(frozen=True)
class GeofenceState:
    level: GeofenceLevel
    zone_id: str | None = None
    zone_code: str | None = None
    zone_name: str | None = None
    distance_m: float | None = None
    inside: bool = False


def px4_ned_to_sim_xy(north_m: float, east_m: float) -> tuple[float, float]:
    """Map PX4 local NED to LOCAL_SIMULATION_METERS_GAZEBO_XY.

    The simulation metadata declares PX4 north as simulation Y and PX4 east as
    simulation X. PX4 local NED is relative to the vehicle home/spawn point, so
    compact-world Gazebo coordinates need the spawn offset added back.
    """
    return PX4_HOME_SIM_X_M + east_m, PX4_HOME_SIM_Y_M + north_m


def is_restricted_zone(zone: dict[str, Any]) -> bool:
    if "restricted" in zone:
        return bool(zone.get("restricted"))
    zone_type = str(zone.get("zoneType") or zone.get("zone_type") or "").strip().upper()
    return zone_type in RESTRICTED_ZONE_TYPES


def normalize_ring(coordinates: Iterable[Iterable[Any]]) -> tuple[tuple[float, float], ...]:
    ring: list[tuple[float, float]] = []
    for point in coordinates:
        values = list(point)
        if len(values) < 2:
            raise ValueError("polygon point must contain x and y")
        x = float(values[0])
        y = float(values[1])
        if not math.isfinite(x) or not math.isfinite(y):
            raise ValueError("polygon point must be finite")
        ring.append((x, y))

    if len(ring) < 3:
        raise ValueError("polygon needs at least 3 points")
    if ring[0] != ring[-1]:
        ring.append(ring[0])
    if len(ring) < 4:
        raise ValueError("closed polygon needs at least 4 points")
    if abs(signed_area(ring)) <= 1e-9:
        raise ValueError("polygon has zero area")
    return tuple(ring)


def zone_from_api(item: dict[str, Any]) -> RestrictedZone:
    return RestrictedZone(
        id=str(item.get("id") or ""),
        code=str(item.get("code") or ""),
        name=str(item.get("name") or item.get("code") or "Restricted zone"),
        zone_type=str(item.get("zoneType") or item.get("zone_type") or ""),
        coordinates=normalize_ring(item.get("coordinates") or []),
    )


def parse_restricted_zones(payload: Any) -> list[RestrictedZone]:
    data = payload.get("data", payload) if isinstance(payload, dict) else payload
    if not isinstance(data, list):
        raise ValueError("zone API response must contain a list")

    zones: list[RestrictedZone] = []
    for item in data:
        if not isinstance(item, dict) or not is_restricted_zone(item):
            continue
        zones.append(zone_from_api(item))
    return zones


def signed_area(ring: Iterable[tuple[float, float]]) -> float:
    points = list(ring)
    return sum(
        (x1 * y2) - (x2 * y1)
        for (x1, y1), (x2, y2) in zip(points, points[1:])
    ) * 0.5


def point_on_segment(
    px: float,
    py: float,
    ax: float,
    ay: float,
    bx: float,
    by: float,
    *,
    eps: float = 1e-9,
) -> bool:
    cross = (px - ax) * (by - ay) - (py - ay) * (bx - ax)
    if abs(cross) > eps:
        return False
    dot = (px - ax) * (px - bx) + (py - ay) * (py - by)
    return dot <= eps


def polygon_covers_point(ring: tuple[tuple[float, float], ...], point: tuple[float, float]) -> bool:
    px, py = point
    inside = False
    for (ax, ay), (bx, by) in zip(ring, ring[1:]):
        if point_on_segment(px, py, ax, ay, bx, by):
            return True
        if (ay > py) != (by > py):
            x_at_y = ax + (py - ay) * (bx - ax) / (by - ay)
            if px < x_at_y:
                inside = not inside
    return inside


def point_segment_distance(
    px: float,
    py: float,
    ax: float,
    ay: float,
    bx: float,
    by: float,
) -> float:
    dx = bx - ax
    dy = by - ay
    length_sq = dx * dx + dy * dy
    if length_sq <= 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length_sq))
    closest_x = ax + t * dx
    closest_y = ay + t * dy
    return math.hypot(px - closest_x, py - closest_y)


def point_polygon_distance(ring: tuple[tuple[float, float], ...], point: tuple[float, float]) -> float:
    px, py = point
    if polygon_covers_point(ring, point):
        return 0.0
    return min(
        point_segment_distance(px, py, ax, ay, bx, by)
        for (ax, ay), (bx, by) in zip(ring, ring[1:])
    )


class RestrictedZoneCache:
    def __init__(self) -> None:
        self._zones: tuple[RestrictedZone, ...] = ()
        self._signature = ""
        self._lock = asyncio.Lock()

    async def snapshot(self) -> tuple[RestrictedZone, ...]:
        async with self._lock:
            return self._zones

    async def replace(self, zones: Iterable[RestrictedZone]) -> bool:
        next_zones = tuple(zones)
        next_signature = zones_signature(next_zones)
        async with self._lock:
            changed = next_signature != self._signature
            if changed:
                self._zones = next_zones
                self._signature = next_signature
            return changed


def zones_signature(zones: Iterable[RestrictedZone]) -> str:
    raw = [
        {
            "id": zone.id,
            "code": zone.code,
            "name": zone.name,
            "zone_type": zone.zone_type,
            "coordinates": zone.coordinates,
        }
        for zone in sorted(zones, key=lambda item: (item.id, item.code))
    ]
    encoded = json.dumps(raw, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


class GeofenceMonitor:
    def __init__(
        self,
        cache: RestrictedZoneCache,
        *,
        caution_distance_m: float = 50.0,
        danger_distance_m: float = 20.0,
        logger: Callable[[str], None] = print,
    ) -> None:
        self.cache = cache
        self.caution_distance_m = caution_distance_m
        self.danger_distance_m = danger_distance_m
        self.logger = logger
        self._state = GeofenceState(GeofenceLevel.UNKNOWN)
        self._state_lock = asyncio.Lock()
        self._last_logged_transition: tuple[str, str | None] | None = None

    async def latest_state(self) -> GeofenceState:
        async with self._state_lock:
            return self._state

    async def evaluate_px4_ned(self, north_m: float, east_m: float) -> GeofenceState:
        sim_point = px4_ned_to_sim_xy(north_m, east_m)
        zones = await self.cache.snapshot()
        state = evaluate_geofence(
            sim_point,
            zones,
            caution_distance_m=self.caution_distance_m,
            danger_distance_m=self.danger_distance_m,
        )
        await self._set_state(state)
        return state

    async def evaluate_sim_xy(self, sim_x: float, sim_y: float) -> GeofenceState:
        zones = await self.cache.snapshot()
        state = evaluate_geofence(
            (sim_x, sim_y),
            zones,
            caution_distance_m=self.caution_distance_m,
            danger_distance_m=self.danger_distance_m,
        )
        await self._set_state(state)
        return state

    async def _set_state(self, state: GeofenceState) -> None:
        async with self._state_lock:
            previous = self._state
            self._state = state

        transition_key = (state.level.value, state.zone_id)
        if previous.level != state.level or previous.zone_id != state.zone_id:
            if transition_key != self._last_logged_transition:
                self._log_transition(previous, state)
                self._last_logged_transition = transition_key

    def _log_transition(self, previous: GeofenceState, state: GeofenceState) -> None:
        zone = state.zone_code or state.zone_name or "-"
        if state.distance_m is None:
            suffix = f" zone={zone}"
        else:
            suffix = f" zone={zone} distance={state.distance_m:.1f}m"
        self.logger(f"[GEOFENCE] {previous.level.value} -> {state.level.value}{suffix}")


def evaluate_geofence(
    sim_point: tuple[float, float],
    zones: Iterable[RestrictedZone],
    *,
    caution_distance_m: float,
    danger_distance_m: float,
) -> GeofenceState:
    nearest_zone: RestrictedZone | None = None
    nearest_distance: float | None = None

    for zone in zones:
        if polygon_covers_point(zone.coordinates, sim_point):
            return GeofenceState(
                level=GeofenceLevel.VIOLATION,
                zone_id=zone.id,
                zone_code=zone.code,
                zone_name=zone.name,
                distance_m=0.0,
                inside=True,
            )
        distance = point_polygon_distance(zone.coordinates, sim_point)
        if nearest_distance is None or distance < nearest_distance:
            nearest_distance = distance
            nearest_zone = zone

    if nearest_zone is None or nearest_distance is None:
        return GeofenceState(level=GeofenceLevel.SAFE)

    if nearest_distance <= danger_distance_m:
        level = GeofenceLevel.DANGER
    elif nearest_distance <= caution_distance_m:
        level = GeofenceLevel.CAUTION
    else:
        level = GeofenceLevel.SAFE

    return GeofenceState(
        level=level,
        zone_id=nearest_zone.id,
        zone_code=nearest_zone.code,
        zone_name=nearest_zone.name,
        distance_m=nearest_distance,
        inside=False,
    )


async def fetch_restricted_zones(client: httpx.AsyncClient, backend_base_url: str) -> list[RestrictedZone]:
    response = await client.get(f"{backend_base_url.rstrip('/')}/api/zones")
    response.raise_for_status()
    return parse_restricted_zones(response.json())


async def zone_refresh_loop(
    cache: RestrictedZoneCache,
    backend_base_urls: Iterable[str],
    *,
    refresh_seconds: float = 5.0,
    logger: Callable[[str], None] = print,
    timeout_s: float = 5.0,
) -> None:
    urls = [url.rstrip("/") for url in backend_base_urls if url]
    last_failure_log_s = 0.0
    first_success = True

    async with httpx.AsyncClient(timeout=timeout_s) as client:
        while True:
            fetched: list[RestrictedZone] | None = None
            last_error: Exception | None = None
            for base_url in urls:
                try:
                    fetched = await fetch_restricted_zones(client, base_url)
                    break
                except Exception as exc:
                    last_error = exc

            if fetched is None:
                now = time.monotonic()
                if now - last_failure_log_s >= 15.0:
                    cached_count = len(await cache.snapshot())
                    logger(
                        "[GEOFENCE] Zone refresh failed, "
                        f"retaining {cached_count} cached restricted zones"
                    )
                    if last_error is not None:
                        logger(f"[GEOFENCE] Last zone refresh error: {last_error}")
                    last_failure_log_s = now
            else:
                changed = await cache.replace(fetched)
                if first_success:
                    logger(f"[GEOFENCE] Loaded restricted zones count={len(fetched)}")
                    for zone in fetched:
                        logger(f"[GEOFENCE] zone={zone.code or zone.id} vertices={len(zone.coordinates)}")
                    first_success = False
                elif changed:
                    logger(f"[GEOFENCE] Restricted zones updated count={len(fetched)}")

            await asyncio.sleep(max(refresh_seconds, 0.5))
