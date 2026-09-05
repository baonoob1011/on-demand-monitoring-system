import asyncio
import logging
import math
import os
import time
from dataclasses import dataclass, field
from typing import Any, Awaitable, Callable

import httpx
from dotenv import load_dotenv
from mavsdk import System


load_dotenv()

PX4_SYSTEM_ADDRESS = os.getenv("PX4_SYSTEM_ADDRESS", "udp://:14540")
MAVSDK_TELEMETRY_GRPC_PORT = int(os.getenv("MAVSDK_TELEMETRY_GRPC_PORT", "50051"))
MAVSDK_TELEMETRY_SYSID = int(os.getenv("MAVSDK_TELEMETRY_SYSID", "245"))
MAVSDK_TELEMETRY_COMPID = int(os.getenv("MAVSDK_TELEMETRY_COMPID", "190"))
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
DEVICE_CODE = os.getenv("DEVICE_CODE", "DRONE-01")
TELEMETRY_INTERVAL_SECONDS = float(os.getenv("TELEMETRY_INTERVAL_SECONDS", "5"))
USE_SITL_BATTERY_SIM = os.getenv("USE_SITL_BATTERY_SIM", "true").lower() in ("1", "true", "yes", "on")
SITL_BATTERY_START_PERCENT = float(os.getenv("SITL_BATTERY_START_PERCENT", "100"))
SITL_BATTERY_MIN_PERCENT = float(os.getenv("SITL_BATTERY_MIN_PERCENT", "30"))
SITL_BATTERY_DRAIN_INTERVAL_SECONDS = float(os.getenv("SITL_BATTERY_DRAIN_INTERVAL_SECONDS", "60"))
SITL_BATTERY_DRAIN_PER_INTERVAL = float(os.getenv("SITL_BATTERY_DRAIN_PER_INTERVAL", "0.01"))

LOGGER = logging.getLogger("telemetry_sender")
TAKEOFF_STARTED_AT_MONOTONIC: float | None = None


@dataclass
class TelemetryState:
    battery_percent: float | None = None

    latitude: float | None = None
    longitude: float | None = None
    absolute_altitude: float | None = None
    relative_altitude: float | None = None

    gps_fix_type: str | None = None
    gps_satellite_count: int | None = None

    gyrometer_ok: bool | None = None
    accelerometer_ok: bool | None = None
    magnetometer_ok: bool | None = None
    local_position_ok: bool | None = None
    global_position_ok: bool | None = None
    home_position_ok: bool | None = None
    armable: bool | None = None

    heading_degree: float | None = None

    velocity_north: float | None = None
    velocity_east: float | None = None
    velocity_down: float | None = None
    ground_speed: float | None = None

    armed: bool | None = None
    flight_mode: str | None = None

    home_latitude: float | None = None
    home_longitude: float | None = None
    home_absolute_altitude: float | None = None
    home_relative_altitude: float | None = None

    roll_degree: float | None = None
    pitch_degree: float | None = None
    yaw_degree: float | None = None

    connected: bool | None = None
    in_air: bool | None = None

    # MAVSDK Python exposes geofence upload/clear APIs, but no telemetry stream
    # for configured/passed state in this installed version. Keep unavailable
    # values explicit instead of faking a preflight result.
    geofence_configured: bool | None = False
    geofence_passed: bool | None = None

    lock: asyncio.Lock = field(default_factory=asyncio.Lock)

    async def update(self, **values: Any) -> None:
        global TAKEOFF_STARTED_AT_MONOTONIC
        async with self.lock:
            if values.get("in_air") is True and self.in_air is not True and TAKEOFF_STARTED_AT_MONOTONIC is None:
                TAKEOFF_STARTED_AT_MONOTONIC = time.monotonic()
            for key, value in values.items():
                setattr(self, key, value)

    async def snapshot(self) -> dict[str, Any]:
        async with self.lock:
            return {
                "batteryPercent": self.battery_percent,
                "latitude": self.latitude,
                "longitude": self.longitude,
                "absoluteAltitude": self.absolute_altitude,
                "relativeAltitude": self.relative_altitude,
                "altitude": self.relative_altitude,
                "gpsFixType": self.gps_fix_type,
                "gpsSatelliteCount": self.gps_satellite_count,
                "gyrometerOk": self.gyrometer_ok,
                "accelerometerOk": self.accelerometer_ok,
                "magnetometerOk": self.magnetometer_ok,
                "localPositionOk": self.local_position_ok,
                "globalPositionOk": self.global_position_ok,
                "homePositionOk": self.home_position_ok,
                "armable": self.armable,
                "headingDegree": self.heading_degree,
                "velocityNorth": self.velocity_north,
                "velocityEast": self.velocity_east,
                "velocityDown": self.velocity_down,
                "groundSpeed": self.ground_speed,
                "speed": self.ground_speed,
                "armed": self.armed,
                "flightMode": self.flight_mode,
                "homeLatitude": self.home_latitude,
                "homeLongitude": self.home_longitude,
                "homeAbsoluteAltitude": self.home_absolute_altitude,
                "homeRelativeAltitude": self.home_relative_altitude,
                "rollDegree": self.roll_degree,
                "pitchDegree": self.pitch_degree,
                "yawDegree": self.yaw_degree,
                "connected": self.connected,
                "inAir": self.in_air,
                "geofenceConfigured": self.geofence_configured,
                "geofencePassed": self.geofence_passed,
            }

    async def has_backend_required_fields(self) -> bool:
        async with self.lock:
            return all(
                value is not None
                for value in (
                    self.latitude,
                    self.longitude,
                    self.relative_altitude,
                    self.armed,
                )
            )


state = TelemetryState()


class MavsdkAckNoiseFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        message = record.getMessage()
        return "Received ack for not-existing command: 512" not in message


def configure_logging() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    logging.getLogger("mavsdk_server").addFilter(MavsdkAckNoiseFilter())


def clean_enum_name(value: Any) -> str | None:
    if value is None:
        return None
    name = getattr(value, "name", None)
    if name:
        return str(name)
    text = str(value)
    return text.rsplit(".", maxsplit=1)[-1]


def normalize_battery_percent(value: float | None) -> float | None:
    if USE_SITL_BATTERY_SIM:
        if TAKEOFF_STARTED_AT_MONOTONIC is None:
            return round(SITL_BATTERY_START_PERCENT, 2)
        elapsed_seconds = time.monotonic() - TAKEOFF_STARTED_AT_MONOTONIC
        drain_steps = math.floor(elapsed_seconds / SITL_BATTERY_DRAIN_INTERVAL_SECONDS)
        simulated_percent = SITL_BATTERY_START_PERCENT - (drain_steps * SITL_BATTERY_DRAIN_PER_INTERVAL)
        return round(max(SITL_BATTERY_MIN_PERCENT, simulated_percent), 2)

    if value is None or math.isnan(value) or value < 0:
        return None
    if value <= 1:
        percent = value * 100
    else:
        percent = value
    return round(percent, 2)


def number_or_none(value: Any) -> float | None:
    if value is None:
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    if math.isnan(result):
        return None
    return result


def first_attr(source: Any, *names: str) -> Any:
    for name in names:
        if hasattr(source, name):
            return getattr(source, name)
    return None


async def run_stream(name: str, stream: Callable[[], Any], handler: Callable[[Any], Awaitable[None]]) -> None:
    while True:
        try:
            async for item in stream():
                await handler(item)
        except asyncio.CancelledError:
            raise
        except Exception:
            LOGGER.warning("%s stream failed; retrying in 2 seconds", name, exc_info=True)
            await asyncio.sleep(2)


async def connect_px4(drone: System) -> None:
    LOGGER.info("Connecting to PX4...")
    await drone.connect(system_address=PX4_SYSTEM_ADDRESS)

    async for connection_state in drone.core.connection_state():
        await state.update(connected=connection_state.is_connected)
        if connection_state.is_connected:
            LOGGER.info("PX4 connected")
            return


async def configure_telemetry_rates(drone: System) -> None:
    rate_calls = (
        ("position", drone.telemetry.set_rate_position, 5.0),
        ("battery", drone.telemetry.set_rate_battery, 1.0),
        ("attitude", drone.telemetry.set_rate_attitude_euler, 5.0),
        ("velocity", drone.telemetry.set_rate_velocity_ned, 5.0),
    )

    for name, setter, rate_hz in rate_calls:
        try:
            await setter(rate_hz)
        except Exception:
            LOGGER.debug("Could not set %s telemetry rate", name, exc_info=True)


async def watch_connection(drone: System) -> None:
    async def handle(connection_state: Any) -> None:
        await state.update(connected=connection_state.is_connected)

    await run_stream("connection", drone.core.connection_state, handle)


async def watch_battery(drone: System) -> None:
    async def handle(battery: Any) -> None:
        await state.update(battery_percent=normalize_battery_percent(battery.remaining_percent))

    await run_stream("battery", drone.telemetry.battery, handle)


async def watch_position(drone: System) -> None:
    async def handle(position: Any) -> None:
        await state.update(
            latitude=number_or_none(position.latitude_deg),
            longitude=number_or_none(position.longitude_deg),
            absolute_altitude=number_or_none(position.absolute_altitude_m),
            relative_altitude=number_or_none(position.relative_altitude_m),
        )

    await run_stream("position", drone.telemetry.position, handle)


async def watch_gps_info(drone: System) -> None:
    async def handle(gps_info: Any) -> None:
        await state.update(
            gps_fix_type=clean_enum_name(gps_info.fix_type),
            gps_satellite_count=gps_info.num_satellites,
        )

    await run_stream("gps_info", drone.telemetry.gps_info, handle)


async def watch_health(drone: System) -> None:
    async def handle(health: Any) -> None:
        await state.update(
            gyrometer_ok=first_attr(health, "is_gyrometer_calibration_ok"),
            accelerometer_ok=first_attr(health, "is_accelerometer_calibration_ok"),
            magnetometer_ok=first_attr(health, "is_magnetometer_calibration_ok"),
            local_position_ok=first_attr(health, "is_local_position_ok"),
            global_position_ok=first_attr(health, "is_global_position_ok"),
            home_position_ok=first_attr(health, "is_home_position_ok"),
            armable=first_attr(health, "is_armable"),
        )

    await run_stream("health", drone.telemetry.health, handle)


async def watch_heading(drone: System) -> None:
    async def handle(heading: Any) -> None:
        await state.update(heading_degree=number_or_none(heading.heading_deg))

    await run_stream("heading", drone.telemetry.heading, handle)


async def watch_velocity(drone: System) -> None:
    async def handle(velocity: Any) -> None:
        north = number_or_none(velocity.north_m_s)
        east = number_or_none(velocity.east_m_s)
        down = number_or_none(velocity.down_m_s)
        ground_speed = None
        if north is not None and east is not None:
            ground_speed = math.sqrt(north**2 + east**2)

        await state.update(
            velocity_north=north,
            velocity_east=east,
            velocity_down=down,
            ground_speed=round(ground_speed, 3) if ground_speed is not None else None,
        )

    await run_stream("velocity_ned", drone.telemetry.velocity_ned, handle)


async def watch_armed(drone: System) -> None:
    async def handle(armed: bool) -> None:
        await state.update(armed=armed)

    await run_stream("armed", drone.telemetry.armed, handle)


async def watch_flight_mode(drone: System) -> None:
    async def handle(flight_mode: Any) -> None:
        await state.update(flight_mode=clean_enum_name(flight_mode))

    await run_stream("flight_mode", drone.telemetry.flight_mode, handle)


async def watch_home(drone: System) -> None:
    async def handle(home: Any) -> None:
        await state.update(
            home_latitude=number_or_none(home.latitude_deg),
            home_longitude=number_or_none(home.longitude_deg),
            home_absolute_altitude=number_or_none(home.absolute_altitude_m),
            home_relative_altitude=number_or_none(first_attr(home, "relative_altitude_m")),
        )

    await run_stream("home", drone.telemetry.home, handle)


async def watch_attitude(drone: System) -> None:
    async def handle(attitude: Any) -> None:
        await state.update(
            roll_degree=number_or_none(attitude.roll_deg),
            pitch_degree=number_or_none(attitude.pitch_deg),
            yaw_degree=number_or_none(attitude.yaw_deg),
        )

    await run_stream("attitude_euler", drone.telemetry.attitude_euler, handle)


async def watch_in_air(drone: System) -> None:
    async def handle(in_air: bool) -> None:
        await state.update(in_air=in_air)

    await run_stream("in_air", drone.telemetry.in_air, handle)


async def send_telemetry() -> None:
    url = f"{BACKEND_BASE_URL}/api/devices/{DEVICE_CODE}/telemetry"
    timeout = httpx.Timeout(5.0)
    sent_count = 0

    LOGGER.info("Sending telemetry for %s every %s seconds", DEVICE_CODE, TELEMETRY_INTERVAL_SECONDS)
    async with httpx.AsyncClient(timeout=timeout) as client:
        while True:
            if not await state.has_backend_required_fields():
                await asyncio.sleep(TELEMETRY_INTERVAL_SECONDS)
                continue

            payload = await state.snapshot()
            try:
                response = await client.post(url, json=payload)
            except httpx.ConnectError:
                LOGGER.warning("Backend unavailable; retrying on next interval")
            except httpx.TimeoutException:
                LOGGER.warning("Backend timeout; retrying on next interval")
            except httpx.HTTPError:
                LOGGER.warning("Backend request failed; retrying on next interval", exc_info=True)
            else:
                if 200 <= response.status_code < 300:
                    sent_count += 1
                    if sent_count == 1 or sent_count % 30 == 0:
                        LOGGER.info("Telemetry updated successfully, HTTP %s", response.status_code)
                else:
                    LOGGER.warning("Backend rejected telemetry, HTTP %s: %s", response.status_code, response.text[:500])

            await asyncio.sleep(TELEMETRY_INTERVAL_SECONDS)


async def main() -> None:
    LOGGER.info("========================================")
    LOGGER.info(" On-Demand Monitoring Telemetry Sender")
    LOGGER.info("========================================")
    LOGGER.info("Device: %s", DEVICE_CODE)
    LOGGER.info("PX4: %s", PX4_SYSTEM_ADDRESS)
    LOGGER.info("MAVSDK gRPC: localhost:%s", MAVSDK_TELEMETRY_GRPC_PORT)
    LOGGER.info("Backend: %s", BACKEND_BASE_URL)
    if USE_SITL_BATTERY_SIM:
        LOGGER.info(
            "SITL battery simulation enabled: start %.1f%%, drain %.2f%% every %.0fs, minimum %.1f%%",
            SITL_BATTERY_START_PERCENT,
            SITL_BATTERY_DRAIN_PER_INTERVAL,
            SITL_BATTERY_DRAIN_INTERVAL_SECONDS,
            SITL_BATTERY_MIN_PERCENT,
        )

    drone = System(
        port=MAVSDK_TELEMETRY_GRPC_PORT,
        sysid=MAVSDK_TELEMETRY_SYSID,
        compid=MAVSDK_TELEMETRY_COMPID,
    )

    await connect_px4(drone)
    await configure_telemetry_rates(drone)

    LOGGER.info("Starting telemetry streams...")
    await asyncio.gather(
        watch_connection(drone),
        watch_battery(drone),
        watch_position(drone),
        watch_gps_info(drone),
        watch_health(drone),
        watch_heading(drone),
        watch_velocity(drone),
        watch_armed(drone),
        watch_flight_mode(drone),
        watch_home(drone),
        watch_attitude(drone),
        watch_in_air(drone),
        send_telemetry(),
    )


if __name__ == "__main__":
    configure_logging()
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        LOGGER.info("Stopped by user")
