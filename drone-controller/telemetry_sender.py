import asyncio
import math
import os
from dataclasses import dataclass, field
from typing import Any

import httpx
from dotenv import load_dotenv
from mavsdk import System


load_dotenv()

PX4_SYSTEM_ADDRESS = os.getenv("PX4_SYSTEM_ADDRESS", "udp://:14540")
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
DEVICE_CODE = os.getenv("DEVICE_CODE", "DRONE-01")
TELEMETRY_INTERVAL_SECONDS = float(os.getenv("TELEMETRY_INTERVAL_SECONDS", "2"))


@dataclass
class TelemetryState:
    latitude: float | None = None
    longitude: float | None = None
    altitude: float | None = None
    battery_percent: float | None = None
    speed: float | None = None
    flight_mode: str | None = None
    armed: bool | None = None
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)

    async def update(self, **values: Any) -> None:
        async with self.lock:
            for key, value in values.items():
                setattr(self, key, value)

    async def snapshot(self) -> dict[str, Any] | None:
        async with self.lock:
            required = (self.latitude, self.longitude, self.altitude, self.armed)
            if any(value is None for value in required):
                return None

            return {
                "latitude": self.latitude,
                "longitude": self.longitude,
                "altitude": self.altitude,
                "batteryPercent": self.battery_percent,
                "speed": self.speed,
                "flightMode": self.flight_mode,
                "armed": self.armed,
            }


state = TelemetryState()


def clean_enum_name(value: Any) -> str:
    name = getattr(value, "name", None)
    if name:
        return str(name)
    text = str(value)
    return text.rsplit(".", maxsplit=1)[-1]


def normalize_battery_percent(value: float | None) -> float | None:
    if value is None or math.isnan(value) or value < 0:
        return None
    if value <= 1:
        return round(value * 100, 2)
    return round(value, 2)


async def connect_px4(drone: System) -> None:
    print("[PX4] Waiting for connection...")
    await drone.connect(system_address=PX4_SYSTEM_ADDRESS)

    async for connection_state in drone.core.connection_state():
        if connection_state.is_connected:
            print("[PX4] Connected")
            return


async def watch_position(drone: System) -> None:
    async for position in drone.telemetry.position():
        await state.update(
            latitude=position.latitude_deg,
            longitude=position.longitude_deg,
            altitude=position.relative_altitude_m,
        )


async def watch_battery(drone: System) -> None:
    async for battery in drone.telemetry.battery():
        await state.update(battery_percent=normalize_battery_percent(battery.remaining_percent))


async def watch_velocity(drone: System) -> None:
    async for velocity in drone.telemetry.velocity_ned():
        speed = math.sqrt(
            velocity.north_m_s**2
            + velocity.east_m_s**2
            + velocity.down_m_s**2
        )
        await state.update(speed=round(speed, 2))


async def watch_flight_mode(drone: System) -> None:
    async for flight_mode in drone.telemetry.flight_mode():
        await state.update(flight_mode=clean_enum_name(flight_mode))


async def watch_armed(drone: System) -> None:
    async for armed in drone.telemetry.armed():
        await state.update(armed=armed)


def print_telemetry(payload: dict[str, Any]) -> None:
    print("\n[TELEMETRY]")
    print(f"lat={payload['latitude']}")
    print(f"lon={payload['longitude']}")
    print(f"alt={payload['altitude']}m")
    print(f"battery={payload['batteryPercent']}%")
    print(f"speed={payload['speed']}m/s")
    print(f"mode={payload['flightMode']}")
    print(f"armed={payload['armed']}")


async def send_telemetry() -> None:
    url = f"{BACKEND_BASE_URL}/api/devices/{DEVICE_CODE}/telemetry"
    timeout = httpx.Timeout(5.0)

    async with httpx.AsyncClient(timeout=timeout) as client:
        while True:
            payload = await state.snapshot()
            if payload is None:
                await asyncio.sleep(TELEMETRY_INTERVAL_SECONDS)
                continue

            print_telemetry(payload)

            try:
                response = await client.post(url, json=payload)
            except httpx.ConnectError:
                print("[BACKEND] Unavailable - retrying later")
            except httpx.TimeoutException:
                print("[BACKEND] Timeout - retrying later")
            except httpx.HTTPError as exc:
                print(f"[BACKEND] Network error - {exc}")
            else:
                if 200 <= response.status_code < 300:
                    print(f"[BACKEND] Telemetry sent - HTTP {response.status_code}")
                else:
                    print(f"[BACKEND] HTTP {response.status_code}")
                    print(response.text)

            await asyncio.sleep(TELEMETRY_INTERVAL_SECONDS)


async def main() -> None:
    print("========================================")
    print(" On-Demand Monitoring Drone Controller")
    print("========================================")
    print(f"Device: {DEVICE_CODE}")
    print(f"PX4: {PX4_SYSTEM_ADDRESS}")
    print(f"Backend: {BACKEND_BASE_URL}\n")

    drone = System()
    await connect_px4(drone)

    await asyncio.gather(
        watch_position(drone),
        watch_battery(drone),
        watch_velocity(drone),
        watch_flight_mode(drone),
        watch_armed(drone),
        send_telemetry(),
    )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[SHUTDOWN] Stopped by user")
