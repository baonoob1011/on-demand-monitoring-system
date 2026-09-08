import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from io import BytesIO
import logging
import math
import os
import socket
import subprocess
import termios
import threading
import tty
import sys
import grpc
import httpx
from mavsdk import System
from mavsdk.action import ActionError
from mavsdk.offboard import OffboardError, VelocityNedYaw
from PIL import Image as PilImage
from pathlib import Path
from dotenv import load_dotenv

PROJECT_ROOT = Path(
    os.getenv(
        "PROJECT_PATH",
        "/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system",
    )
)

DRONE_DIR = PROJECT_ROOT / "drone"

if "/usr/lib/python3/dist-packages" not in sys.path:
    sys.path.append("/usr/lib/python3/dist-packages")

if str(DRONE_DIR) not in sys.path:
    sys.path.insert(0, str(DRONE_DIR))

ENV_FILE = PROJECT_ROOT / "ondemandmonitoring" / ".env"

load_dotenv(ENV_FILE)

print(
    f"[ENV] Loaded: {ENV_FILE}",
    flush=True,
)

print(
    "[ENV] Simple obstacle-stop mission mode",
    flush=True,
)
try:
    from obstacle_avoidance.lidar_gateway import LidarGateway
    from obstacle_avoidance.avoidance_controller import AvoidanceController
    from obstacle_avoidance.sensor_reader import (
        OBSTACLE_DISTANCE_M,
        WARNING_DISTANCE_M,
        front_obstacle_reading,
    )
except ImportError as exc:
    LidarGateway = None
    AvoidanceController = None
    OBSTACLE_DISTANCE_M = 5.0
    WARNING_DISTANCE_M = 10.0
    front_obstacle_reading = None
    LIDAR_IMPORT_ERROR = exc
else:
    LIDAR_IMPORT_ERROR = None


class MotionOwner(Enum):
    MANUAL = "MANUAL"
    EMERGENCY = "EMERGENCY"


@dataclass(frozen=True)
class SavedMotion:
    forward_m_s: float
    right_m_s: float
    down_m_s: float
    north_m_s: float
    east_m_s: float
    yaw_deg: float

try:
    from gz.msgs10.image_pb2 import Image as GzImage
    from gz.transport13 import Node
except ImportError:
    GzImage = None
    Node = None


load_dotenv()
PX4_CONTROL_SYSTEM_ADDRESS = os.getenv(
    "PX4_CONTROL_SYSTEM_ADDRESS",
    "udpin://0.0.0.0:14030",
)

MAVSDK_CONTROL_GRPC_PORT = int(
    os.getenv("MAVSDK_CONTROL_GRPC_PORT", "50052")
)
MAVSDK_CONTROL_SYSID = int(
    os.getenv("MAVSDK_CONTROL_SYSID", "245")
)
MAVSDK_CONTROL_COMPID = int(
    os.getenv("MAVSDK_CONTROL_COMPID", "191")
)

MOVE_SPEED_M_S = float(
    os.getenv("CONTROL_MOVE_SPEED_M_S", "6.0")
)
VERTICAL_SPEED_M_S = float(
    os.getenv("CONTROL_VERTICAL_SPEED_M_S", "2.0")
)
YAW_STEP_DEG = float(
    os.getenv("CONTROL_YAW_STEP_DEG", "30.0")
)

SPEED_ADJUST_STEP_M_S = float(
    os.getenv("CONTROL_SPEED_ADJUST_STEP_M_S", "200.0")
)

SAFETY_POLL_INTERVAL_S = float(
    os.getenv("SAFETY_POLL_INTERVAL_S", "0.5")
)

MISSION_CRUISE_SPEED_M_S = float(
    os.getenv("MISSION_CRUISE_SPEED_M_S", "2.0")
)

MISSION_WARNING_DISTANCE_M = float(
    os.getenv("MISSION_WARNING_DISTANCE_M", "80.0")
)

MISSION_WARNING_MIN_SPEED_M_S = float(
    os.getenv("MISSION_WARNING_MIN_SPEED_M_S", "1.0")
)

MISSION_WARNING_MAX_SPEED_M_S = float(
    os.getenv("MISSION_WARNING_MAX_SPEED_M_S", "5.0")
)

MISSION_GOAL_SLOWDOWN_DISTANCE_M = float(
    os.getenv("MISSION_GOAL_SLOWDOWN_DISTANCE_M", "15.0")
)

MISSION_GOAL_MIN_SPEED_M_S = float(
    os.getenv("MISSION_GOAL_MIN_SPEED_M_S", "1.0")
)

MISSION_CLIMB_FIRST_TIMEOUT_S = float(
    os.getenv("MISSION_CLIMB_FIRST_TIMEOUT_S", "60.0")
)

MISSION_CLIMB_MAX_OVERSHOOT_M = float(
    os.getenv("MISSION_CLIMB_MAX_OVERSHOOT_M", "2.0")
)

MISSION_ALTITUDE_TOLERANCE_M = float(
    os.getenv("MISSION_ALTITUDE_TOLERANCE_M", "1.0")
)

MISSION_ALTITUDE_HOLD_DEADBAND_M = float(
    os.getenv("MISSION_ALTITUDE_HOLD_DEADBAND_M", "0.35")
)

MISSION_ALTITUDE_HOLD_MAX_VERTICAL_SPEED_M_S = float(
    os.getenv("MISSION_ALTITUDE_HOLD_MAX_VERTICAL_SPEED_M_S", "1.0")
)

MISSION_FORWARD_START_SPEED_M_S = float(
    os.getenv("MISSION_FORWARD_START_SPEED_M_S", "3.0")
)

MISSION_FORWARD_RAMP_SECONDS = float(
    os.getenv("MISSION_FORWARD_RAMP_SECONDS", "8.0")
)

MISSION_LAUNCH_PAD_CLEAR_RADIUS_M = float(
    os.getenv("MISSION_LAUNCH_PAD_CLEAR_RADIUS_M", "14.0")
)

MISSION_LAUNCH_PAD_MAX_SPEED_M_S = float(
    os.getenv("MISSION_LAUNCH_PAD_MAX_SPEED_M_S", "3.0")
)

MISSION_POLL_INTERVAL_S = float(
    os.getenv("MISSION_POLL_INTERVAL_S", "5.0")
)

MISSION_ARRIVAL_RADIUS_M = float(
    os.getenv("MISSION_ARRIVAL_RADIUS_M", "3.0")
)

BACKEND_BASE_URL = os.getenv(
    "BACKEND_BASE_URL",
    "http://localhost:8080",
).rstrip("/")

DEVICE_CODE = os.getenv(
    "DEVICE_CODE",
    "DRONE-01",
)

DRONE_ID = os.getenv(
    "DRONE_ID",
    DEVICE_CODE,
)

MISSION_ID = os.getenv(
    "MISSION_ID",
    "MISSION_001",
)

SIM_WORLD = os.getenv(
    "SIM_WORLD",
    "legacy",
)

DEFAULT_GAZEBO_WORLD = (
    "forest_monitoring_compact"
    if SIM_WORLD == "compact"
    else "forest_monitoring"
)

CAMERA_TOPIC = os.getenv(
    "GAZEBO_CAMERA_TOPIC",
    f"/world/{DEFAULT_GAZEBO_WORLD}/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image",
)

class MavsdkAckNoiseFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        message = record.getMessage()
        return "Received ack for not-existing command: 512" not in message


def configure_mavsdk_logging() -> None:
    try:
        sys.stdout.reconfigure(line_buffering=True)
        sys.stderr.reconfigure(line_buffering=True)
    except AttributeError:
        pass

    logging.basicConfig(level=logging.WARNING, format="%(message)s")
    logging.getLogger("mavsdk_server").addFilter(MavsdkAckNoiseFilter())


def env_bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in ("1", "true", "yes", "on")


def backend_url_candidates() -> list[str]:
    configured = BACKEND_BASE_URL.rstrip("/")
    candidates = [configured]

    if configured in {"http://localhost:8080", "http://127.0.0.1:8080"}:
        candidates.append("http://host.docker.internal:8080")
        candidates.append("http://172.20.176.1:8080")
        try:
            output = subprocess.check_output(
                ["sh", "-lc", "awk '/^nameserver / {print $2; exit}' /etc/resolv.conf"],
                text=True,
                timeout=1.0,
            ).strip()
            if output:
                candidates.append(f"http://{output}:8080")
        except (OSError, subprocess.SubprocessError):
            pass

    deduped = []
    for url in candidates:
        if url and url not in deduped:
            deduped.append(url)
    return deduped


def read_key() -> str:
    fd = sys.stdin.fileno()
    old_settings = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        return sys.stdin.read(1).lower()
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)


class CameraGateway:
    def __init__(self) -> None:
        self.latest_frame: GzImage | None = None
        self.lock = threading.Lock()
        self.node = None
        self.upload_url = f"{BACKEND_BASE_URL}/api/missions/{MISSION_ID}/images"

    def start(self) -> None:
        if Node is None or GzImage is None:
            print("[CAMERA] Gazebo Python bindings not found")
            print("[CAMERA] Run with PYTHONPATH=/usr/lib/python3/dist-packages")
            return

        self.node = Node()
        self.node.subscribe(GzImage, CAMERA_TOPIC, self._on_frame)
        print(f"[CAMERA] Listening to drone sensor: {CAMERA_TOPIC}")

    def _on_frame(self, msg: GzImage, *_args) -> None:
        with self.lock:
            self.latest_frame = msg

    def _latest_jpeg(self) -> bytes | None:
        with self.lock:
            frame = self.latest_frame

        if frame is None:
            return None

        width = int(frame.width)
        height = int(frame.height)
        raw = bytes(frame.data)
        expected_rgb = width * height * 3
        expected_rgba = width * height * 4

        if len(raw) == expected_rgba:
            image = PilImage.frombytes("RGBA", (width, height), raw).convert("RGB")
        elif len(raw) == expected_rgb:
            image = PilImage.frombytes("RGB", (width, height), raw)
        else:
            print(f"[CAMERA] Unsupported frame size: {len(raw)} bytes for {width}x{height}")
            return None

        output = BytesIO()
        image.save(output, format="JPEG", quality=88)
        return output.getvalue()

    async def capture_and_upload(self) -> None:
        print("[CAMERA] Drone camera capture requested")
        jpeg = await asyncio.to_thread(self._latest_jpeg)
        if jpeg is None:
            print("[CAMERA] No camera frame available")
            return

        captured_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        filename = f"{DRONE_ID}-downward-{timestamp}.jpg"
        data = {
            "droneId": DRONE_ID,
            "capturedAt": captured_at,
        }
        files = {"image": (filename, jpeg, "image/jpeg")}

        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(self.upload_url, data=data, files=files)
        except httpx.ConnectError:
            print("[CAMERA] Backend unavailable")
            return
        except httpx.TimeoutException:
            print("[CAMERA] Upload timeout")
            return
        except httpx.HTTPError as exc:
            print(f"[CAMERA] Upload failed: {exc}")
            return

        if 200 <= response.status_code < 300:
            try:
                payload = response.json()
            except ValueError:
                print("[CAMERA] Image uploaded successfully")
                return

            image = payload.get("data") or {}
            storage_provider = image.get("storageProvider", "UNKNOWN")
            print(f"[CAMERA] Image uploaded successfully ({storage_provider})")
            if storage_provider == "S3":
                print(f"[CAMERA] S3 bucket: {image.get('s3Bucket')}")
                print(f"[CAMERA] S3 key: {image.get('s3Key')}")
            elif storage_provider == "LOCAL":
                print(f"[CAMERA] Local file: {image.get('s3Url')}")
            return

        print(f"[CAMERA] Upload failed - HTTP {response.status_code}")
        print(response.text[:500])


async def connect_px4(drone: System) -> None:
    print("[PX4] Waiting for control connection...")

    try:
        await drone.connect()

        async with asyncio.timeout(20):
            async for state in drone.core.connection_state():
                if state.is_connected:
                    print("[PX4] Control connected")
                    break
    except (asyncio.TimeoutError, grpc.aio.AioRpcError) as exc:
        print_mavsdk_unavailable("PX4 init", exc)
        print("[WARN] Continuing in degraded mode")
        print("[WARN] Arm/takeoff will perform their own readiness checks")
        return

    print("[PX4] Waiting for local position (up to 60s)...")
    try:
        async with asyncio.timeout(60):
            async for health in drone.telemetry.health():
                status = (
                    f"  gps={'OK' if health.is_global_position_ok else 'wait'}"
                    f"  local={'OK' if health.is_local_position_ok else 'wait'}"
                    f"  accel={'OK' if health.is_accelerometer_calibration_ok else 'wait'}"
                    f"  home={'OK' if health.is_home_position_ok else 'wait'}"
                )
                print(f"[PX4] Health: {status}", flush=True)
                if health.is_local_position_ok and health.is_global_position_ok:
                    print("\n[PX4] Local position OK - Ready to fly!")
                    return
    except (asyncio.TimeoutError, grpc.aio.AioRpcError):
        print("\n[WARN] PX4 telemetry health stream unavailable")
        print("[WARN] Continuing in degraded mode")
        print("[WARN] Arm/takeoff will perform their own readiness checks")


def print_command_denied(command: str, exc: Exception) -> None:
    print(f"[WARN] {command} failed: {exc}")
    print("[HINT] Check PX4 terminal shows 'Ready for takeoff!' then press t again.")


def print_mavsdk_unavailable(command: str, exc: Exception) -> None:
    code = exc.code().name if isinstance(exc, grpc.aio.AioRpcError) else type(exc).__name__
    details = exc.details() if isinstance(exc, grpc.aio.AioRpcError) else str(exc)
    print(f"[WARN] {command} failed: MAVSDK connection unavailable ({code}: {details})")
    print("[HINT] PX4/Gazebo may still be running. Restart only the Flight Control tab, or rerun start-drone-stack.cmd.")


def is_grpc_unavailable(exc: Exception) -> bool:
    if not isinstance(exc, grpc.aio.AioRpcError):
        return False

    if exc.code() == grpc.StatusCode.UNAVAILABLE:
        return True

    details = (exc.details() or "").lower()
    return any(
        message in details
        for message in (
            "stream removed",
            "socket closed",
            "connection reset",
            "connection refused",
        )
    )


async def wait_for_grpc_port(timeout_seconds: float = 12.0) -> bool:
    deadline = asyncio.get_running_loop().time() + timeout_seconds
    while asyncio.get_running_loop().time() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", MAVSDK_CONTROL_GRPC_PORT), timeout=0.5):
                return True
        except OSError:
            await asyncio.sleep(0.5)
    return False


class MavsdkConnectionManager:
    def __init__(self) -> None:
        self.drone: System | None = None
        self.lock = asyncio.Lock()
        self.generation = 0
        self.last_reconnect_attempt_s = 0.0
        self.last_unavailable_log_s = 0.0

    def _new_drone(self) -> System:
        return System(
            mavsdk_server_address="localhost",
            port=MAVSDK_CONTROL_GRPC_PORT,
            sysid=MAVSDK_CONTROL_SYSID,
            compid=MAVSDK_CONTROL_COMPID,
        )

    async def connect(self) -> System:
        async with self.lock:
            drone = self._new_drone()
            await connect_px4(drone)
            self.drone = drone
            self.generation += 1
            return drone

    async def reconnect(self) -> System | None:
        async with self.lock:
            now = asyncio.get_running_loop().time()
            wait_s = 3.0 - (now - self.last_reconnect_attempt_s)
            if wait_s > 0:
                await asyncio.sleep(wait_s)

            self.last_reconnect_attempt_s = asyncio.get_running_loop().time()

            if self.last_reconnect_attempt_s - self.last_unavailable_log_s >= 3.0:
                print("[MAVSDK-CLIENT] gRPC connection lost")
                print("[MAVSDK-CLIENT] Waiting for server...")
                self.last_unavailable_log_s = self.last_reconnect_attempt_s

            if not await wait_for_grpc_port(20.0):
                if asyncio.get_running_loop().time() - self.last_unavailable_log_s >= 3.0:
                    print("[ERR] MAVSDK control bridge unavailable")
                    self.last_unavailable_log_s = asyncio.get_running_loop().time()
                return None

            print("[MAVSDK-CLIENT] Creating new System connection")
            drone = self._new_drone()

            try:
                await drone.connect()
                async with asyncio.timeout(20):
                    async for state in drone.core.connection_state():
                        if state.is_connected:
                            self.drone = drone
                            self.generation += 1
                            print("[MAVSDK-CLIENT] PX4 reconnected")
                            return drone
            except (asyncio.TimeoutError, grpc.aio.AioRpcError) as exc:
                print_mavsdk_unavailable("MAVSDK client reconnect", exc)

            print("[ERR] MAVSDK control bridge unavailable")
            return None

    async def get_drone(self) -> System | None:
        return self.drone


async def ensure_offboard_started(drone: System) -> None:
    await drone.offboard.set_velocity_ned(VelocityNedYaw(0.0, 0.0, 0.0, 0.0))
    try:
        await drone.offboard.start()
    except OffboardError as exc:
        if exc._result.result_str != "BUSY":
            raise


def is_climb_only_command(
        north_m_s: float,
        east_m_s: float,
        down_m_s: float,
) -> bool:
    return (
            math.hypot(north_m_s, east_m_s) <= 1e-6
            and down_m_s < -1e-6
    )


async def check_readiness(drone: System) -> bool:
    for attempt in range(2):
        try:
            async with asyncio.timeout(4.0):
                async for state in drone.core.connection_state():
                    if state.is_connected:
                        return True
                    await asyncio.sleep(0.1)
        except asyncio.TimeoutError:
            print("[WARN] MAVSDK is reachable but PX4 is disconnected.")
            print("[ERR] Readiness check timed out. mavsdk_server may be unresponsive.")
        except grpc.aio.AioRpcError as exc:
            print(f"[WARN] mavsdk_server unavailable. gRPC error: {exc.code().name}")

        if attempt < 1:
            print("[HINT] Waiting for control bridge to recover...")
            await asyncio.sleep(1.0)

    return False


async def safe_arm(manager: MavsdkConnectionManager) -> System | None:
    """Arm with retry and clear error reporting."""
    drone = await manager.get_drone()
    if drone is None:
        drone = await manager.reconnect()
        if drone is None:
            print("[ERR] Arm cancelled")
            return None

    reconnect_attempts = 0

    for attempt in range(3):
        try:
            if not await check_readiness(drone):
                refreshed = await manager.reconnect()
                if refreshed is None:
                    print("[ERR] MAVSDK control bridge unavailable")
                    print("[ERR] Arm cancelled")
                    return None
                drone = refreshed

            print(f"[CMD] Arming... (attempt {attempt+1}/3)")
            await drone.action.arm()
            print("[CMD] Armed successfully!")
            return drone
        except ActionError as e:
            print(f"[WARN] Arm failed: {e}")
            if attempt < 2:
                print("[HINT] Retrying in 2s...")
                await asyncio.sleep(2)
        except grpc.aio.AioRpcError as exc:
            print_mavsdk_unavailable("arm", exc)
            if is_grpc_unavailable(exc) and reconnect_attempts < 2:
                reconnect_attempts += 1
                refreshed = await manager.reconnect()
                if refreshed is None:
                    print("[ERR] Arm cancelled")
                    return None
                drone = refreshed
                print("[HINT] Retrying arm with new MAVSDK connection...")
                continue
            print("[ERR] MAVSDK control bridge unavailable")
            print("[ERR] Arm cancelled")
            return None

    print("[ERR] Could not arm after 3 attempts.")
    print("[ERR] Ensure PX4 terminal shows: 'Ready for takeoff!'")
    print("[ERR] Check: sensors OK, no safety switch active.")
    return None

async def set_motion(
        manager: MavsdkConnectionManager,
        north_m_s: float,
        east_m_s: float,
        down_m_s: float,
        yaw_deg: float,
) -> System | None:
    drone = await manager.get_drone()

    if drone is None:
        drone = await manager.reconnect()
        if drone is None:
            return None

    try:
        # ============================================================
        # START OFFBOARD CHỈ 1 LẦN CHO MỖI MAVSDK GENERATION
        #
        # Không được gửi zero setpoint ở mỗi control loop.
        # Reconnect -> manager.generation tăng -> start lại đúng 1 lần.
        # ============================================================
        if (
                getattr(manager, "_offboard_generation", -1)
                != manager.generation
        ):
            await ensure_offboard_started(drone)
            manager._offboard_generation = manager.generation

            print(
                f"[OFFBOARD] Started generation={manager.generation}",
                flush=True,
            )

        # ============================================================
        # GỬI COMMAND THẬT
        # ============================================================
        await drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                north_m_s,
                east_m_s,
                down_m_s,
                yaw_deg,
            )
        )

        return drone

    except grpc.aio.AioRpcError as exc:
        if not is_grpc_unavailable(exc):
            raise

        print_mavsdk_unavailable("movement", exc)

        drone = await manager.reconnect()
        if drone is None:
            return None

        try:
            # manager.generation đã đổi sau reconnect,
            # nên System mới phải start OFFBOARD lại đúng 1 lần.
            if (
                    getattr(manager, "_offboard_generation", -1)
                    != manager.generation
            ):
                await ensure_offboard_started(drone)
                manager._offboard_generation = manager.generation

                print(
                    f"[OFFBOARD] Restarted generation={manager.generation}",
                    flush=True,
                )

            await drone.offboard.set_velocity_ned(
                VelocityNedYaw(
                    north_m_s,
                    east_m_s,
                    down_m_s,
                    yaw_deg,
                )
            )

            return drone

        except grpc.aio.AioRpcError as retry_exc:
            if not is_grpc_unavailable(retry_exc):
                raise

            print(
                "[WARN] movement retry skipped: MAVSDK still unavailable",
                flush=True,
            )
            return None
async def track_local_position(
        manager: MavsdkConnectionManager,
        update_position,
) -> None:
    active_generation = -1

    while True:
        drone = await manager.get_drone()

        if drone is None:
            await asyncio.sleep(0.5)
            continue

        generation = manager.generation

        # MAVSDK System đã đổi sau reconnect -> bỏ stream cũ.
        if generation != active_generation:
            active_generation = generation
            print(
                f"[POSITION] Telemetry generation={generation}",
                flush=True,
            )

        try:
            stream = drone.telemetry.position_velocity_ned()

            while generation == manager.generation:
                try:
                    sample = await asyncio.wait_for(
                        anext(stream),
                        timeout=2.0,
                    )
                except StopAsyncIteration:
                    break
                except asyncio.TimeoutError:
                    # Stream cũ/stale -> mở lại stream từ System hiện tại.
                    break

                position = sample.position

                update_position(
                    float(position.north_m),
                    float(position.east_m),
                    float(position.down_m),
                )

        except asyncio.CancelledError:
            raise

        except (AttributeError, grpc.aio.AioRpcError):
            await asyncio.sleep(0.5)
async def mission_poll_loop(
        is_mission_enabled,
        has_active_mission,
        set_active_mission,
) -> None:
    last_error_log_s = 0.0
    active_backend_url = None
    completed_or_dispatched_missions: set[str] = set()

    while True:
        await asyncio.sleep(MISSION_POLL_INTERVAL_S)

        if not is_mission_enabled() or has_active_mission():
            continue

        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = None
                last_exc = None

                candidates = (
                    [active_backend_url]
                    if active_backend_url
                    else []
                )

                candidates.extend(
                    url
                    for url in backend_url_candidates()
                    if url not in candidates
                )

                for base_url in candidates:
                    try:
                        response = await client.get(
                            f"{base_url}/api/missions/next",
                            params={
                                "deviceCode": DEVICE_CODE,
                            },
                        )

                        active_backend_url = base_url
                        break

                    except httpx.HTTPError as exc:
                        last_exc = exc

                if response is None:
                    raise (
                            last_exc
                            or httpx.ConnectError(
                        "No backend URL candidates available"
                    )
                    )

            if response.status_code >= 400:
                raise httpx.HTTPStatusError(
                    "mission dispatch failed",
                    request=response.request,
                    response=response,
                )

            payload = response.json().get("data") or {}

            target_north = payload.get("targetNorthM")
            target_east = payload.get("targetEastM")
            target_altitude = payload.get("targetAltitudeM")

            mission_code = (
                    payload.get("missionCode")
                    or payload.get("id")
                    or "UNKNOWN"
            )

            if mission_code in completed_or_dispatched_missions:
                continue

            if target_north is None or target_east is None:
                print(
                    f"[MISSION] Ignored mission without local target: "
                    f"{mission_code}",
                    flush=True,
                )
                continue

            completed_or_dispatched_missions.add(
                mission_code
            )

            set_active_mission(
                {
                    "missionCode": mission_code,
                    "targetNorthM": float(target_north),
                    "targetEastM": float(target_east),
                    "targetAltitudeM": (
                        None
                        if target_altitude is None
                        else float(target_altitude)
                    ),
                }
            )

            print(
                f"[MISSION] Dispatched {mission_code}: "
                f"backend={active_backend_url} "
                f"N={float(target_north):.1f} "
                f"E={float(target_east):.1f} "
                f"ALT="
                f"{target_altitude if target_altitude is not None else 'hold'}",
                flush=True,
            )

        except (httpx.HTTPError, ValueError) as exc:
            now = asyncio.get_running_loop().time()

            if now - last_error_log_s >= 10.0:
                print(
                    f"[MISSION] Backend mission poll unavailable: {exc}",
                    flush=True,
                )
                last_error_log_s = now
def body_velocity(
        forward: float,
        right: float,
        yaw_deg: float,
) -> tuple[float, float]:
    yaw_rad = math.radians(yaw_deg)

    north = (
            forward * math.cos(yaw_rad)
            - right * math.sin(yaw_rad)
    )

    east = (
            forward * math.sin(yaw_rad)
            + right * math.cos(yaw_rad)
    )

    return north, east


def is_forward_blocked(state) -> bool:
    return state.front <= OBSTACLE_DISTANCE_M


def is_front_obstacle_direction(direction: str) -> bool:
    return direction == "FRONT"


def should_handle_front_obstacle(state, direction: str) -> bool:
    return is_forward_blocked(state) and is_front_obstacle_direction(direction)


def warning_speed_scale(front_distance_m: float) -> float:
    if front_distance_m <= OBSTACLE_DISTANCE_M:
        return 0.0

    warning_span = max(WARNING_DISTANCE_M - OBSTACLE_DISTANCE_M, 0.1)
    ratio = (front_distance_m - OBSTACLE_DISTANCE_M) / warning_span
    ratio = max(0.0, min(1.0, ratio))

    cruise_speed = max(MISSION_CRUISE_SPEED_M_S, 0.1)
    min_scale = max(0.0, min(1.0, MISSION_WARNING_MIN_SPEED_M_S / cruise_speed))
    return min_scale + ratio * (1.0 - min_scale)


def scale_horizontal_motion(saved_motion: SavedMotion, scale: float) -> SavedMotion:
    return SavedMotion(
        forward_m_s=saved_motion.forward_m_s * scale,
        right_m_s=saved_motion.right_m_s * scale,
        down_m_s=saved_motion.down_m_s,
        north_m_s=saved_motion.north_m_s * scale,
        east_m_s=saved_motion.east_m_s * scale,
        yaw_deg=saved_motion.yaw_deg,
    )


async def mission_motion_loop(
        manager: MavsdkConnectionManager,
        get_saved_motion,
        get_yaw,
        is_mission_enabled,
        get_motion_owner,
        get_safety_speed_scale,
        set_avoidance_drone,
) -> None:
    last_command_log_s = 0.0
    unavailable_reported = False

    while True:
        await asyncio.sleep(SAFETY_POLL_INTERVAL_S)

        if not is_mission_enabled():
            continue

        if get_motion_owner() == MotionOwner.EMERGENCY:
            continue

        saved_motion = get_saved_motion()
        if saved_motion is None:
            continue

        saved_motion = scale_horizontal_motion(
            saved_motion,
            get_safety_speed_scale(),
        )

        try:
            active_drone = await set_motion(
                manager,
                saved_motion.north_m_s,
                saved_motion.east_m_s,
                saved_motion.down_m_s,
                get_yaw(),
            )
            if active_drone is not None:
                set_avoidance_drone(active_drone)

            if unavailable_reported:
                print("[MISSION] MAVSDK control restored", flush=True)
                unavailable_reported = False

            now_s = asyncio.get_running_loop().time()
            if now_s - last_command_log_s >= 2.0:
                last_command_log_s = now_s
                print(
                    f"[MISSION] SIMPLE CMD "
                    f"N={saved_motion.north_m_s:.2f} "
                    f"E={saved_motion.east_m_s:.2f} "
                    f"D={saved_motion.down_m_s:.2f} "
                    f"YAW_HOLD={get_yaw():.0f}",
                    flush=True,
                )

        except OffboardError as exc:
            print_command_denied("mission motion", exc)
        except grpc.aio.AioRpcError as exc:
            if is_grpc_unavailable(exc):
                if not unavailable_reported:
                    print("[MISSION] MAVSDK unavailable - reconnecting")
                    unavailable_reported = True
                await manager.reconnect()
            else:
                print_mavsdk_unavailable("mission motion", exc)


async def obstacle_safety_loop(
        avoidance,
        lidar,
        manager: MavsdkConnectionManager,
        get_yaw,
        get_saved_motion,
        is_safety_sensor_enabled,
        get_motion_owner,
        set_motion_owner,
        stop_manual_motion,
        set_safety_speed_scale,
        is_launch_pad_clear,
):
    last_status = "CLEAR"
    unavailable_reported = False
    ready_reported = False
    mavsdk_unavailable_reported = False
    last_warning_log_s = 0.0

    while True:
        if not is_safety_sensor_enabled():
            last_status = "CLEAR"
            set_safety_speed_scale(1.0)
            await asyncio.sleep(SAFETY_POLL_INTERVAL_S)
            continue

        if not is_launch_pad_clear():
            last_status = "CLEAR"
            set_safety_speed_scale(1.0)
            await asyncio.sleep(SAFETY_POLL_INTERVAL_S)
            continue

        state, status, direction = lidar.snapshot()

        if state is None:
            if not getattr(lidar, "available", False) and not unavailable_reported:
                print("[SAFETY] 2D LiDAR unavailable")
                unavailable_reported = True

            await asyncio.sleep(1.0)
            continue

        if not ready_reported:
            print("[SAFETY] 2D LiDAR emergency guard ready")
            ready_reported = True

        front_blocked = should_handle_front_obstacle(state, direction)

        if front_blocked and get_motion_owner() != MotionOwner.EMERGENCY:
            set_safety_speed_scale(0.0)
            set_motion_owner(MotionOwner.EMERGENCY)
            avoidance.set_yaw(get_yaw())
            stop_manual_motion(clear_saved=False)

            try:
                new_drone = await set_motion(manager, 0.0, 0.0, 0.0, get_yaw())
                if new_drone is not None:
                    avoidance.set_drone(new_drone)
                    await avoidance.hover(log=True)

                if mavsdk_unavailable_reported:
                    print("[SAFETY] MAVSDK control restored")
                    mavsdk_unavailable_reported = False

                print(
                    f"[SAFETY] OBSTACLE_STOP status={status} direction={direction} "
                    f"front={state.front:.2f}m -> HOVER",
                    flush=True,
                )

            except grpc.aio.AioRpcError as exc:
                if is_grpc_unavailable(exc):
                    if not mavsdk_unavailable_reported:
                        print("[SAFETY] MAVSDK unavailable - cannot send hover command")
                        mavsdk_unavailable_reported = True

                    new_drone = await manager.reconnect()
                    if new_drone is not None:
                        avoidance.set_drone(new_drone)
                else:
                    print(f"[SAFETY] Hover failed: {exc}")
            except Exception as exc:
                print(f"[SAFETY] Hover failed: {exc}")

        elif status == "WARNING" and direction == "FRONT":
            scale = warning_speed_scale(state.front)
            set_safety_speed_scale(scale)
            saved_motion = get_saved_motion()

            if saved_motion is not None and get_motion_owner() == MotionOwner.MANUAL:
                slowed = scale_horizontal_motion(saved_motion, scale)
                try:
                    new_drone = await set_motion(
                        manager,
                        slowed.north_m_s,
                        slowed.east_m_s,
                        slowed.down_m_s,
                        get_yaw(),
                    )
                    if new_drone is not None:
                        avoidance.set_drone(new_drone)
                except grpc.aio.AioRpcError as exc:
                    if is_grpc_unavailable(exc):
                        if not mavsdk_unavailable_reported:
                            print("[SAFETY] MAVSDK unavailable - cannot slow down")
                            mavsdk_unavailable_reported = True
                        await manager.reconnect()
                    else:
                        print(f"[SAFETY] Slowdown failed: {exc}")
                except Exception as exc:
                    print(f"[SAFETY] Slowdown failed: {exc}")

            now_s = asyncio.get_running_loop().time()
            if now_s - last_warning_log_s >= 1.0:
                print(
                    f"[SAFETY] WARNING front={state.front:.2f}m "
                    f"-> smooth slowdown scale={scale:.2f}",
                    flush=True,
                )
                last_warning_log_s = now_s

        if status == "CLEAR":
            set_safety_speed_scale(1.0)
            if get_motion_owner() == MotionOwner.EMERGENCY:
                set_motion_owner(MotionOwner.MANUAL)
            if last_status != "CLEAR":
                print("[SAFETY] Path clear", flush=True)

        last_status = status
        await asyncio.sleep(SAFETY_POLL_INTERVAL_S)


async def main() -> None:
    print("========================================")
    print(" On-Demand Monitoring Flight Controller")
    print("========================================")
    print(f"PX4 control: {PX4_CONTROL_SYSTEM_ADDRESS}")
    print(f"MAVSDK gRPC: localhost:{MAVSDK_CONTROL_GRPC_PORT}")
    print()
    print("Keys: t takeoff | w forward | s back | a left | d right")
    print("      f up | v down | q yaw left | e yaw right | k stop")
    print("      o toggle safety sensor")
    print("      1 speed up 200m/s | 2 speed down 200m/s")
    print("      p photo | l land | x exit")
    print()
    print("Press one move key once to keep moving. Press k to stop/hover.")
    print()

    connection_manager = MavsdkConnectionManager()
    drone = await connection_manager.connect()

    camera = CameraGateway()
    camera.start()

    current_yaw_deg = 0.0

    def set_current_yaw(yaw_deg: float) -> None:
        nonlocal current_yaw_deg
        current_yaw_deg = yaw_deg % 360.0

    # ================================================================
    # CURRENT / PREVIOUS MANUAL FLIGHT COMMAND
    # ================================================================

    current_forward_m_s = 0.0
    current_right_m_s = 0.0
    current_north_m_s = 0.0
    current_east_m_s = 0.0
    current_down_m_s = 0.0
    control_speed_m_s = MOVE_SPEED_M_S

    safety_task = None
    position_task = None
    avoidance = None
    lidar = None
    safety_sensor_enabled = (
    os.getenv("SAFETY_SENSOR_ENABLED", "true").strip().lower()
        in {"1", "true", "yes", "on"}
    )

    motion_owner = MotionOwner.MANUAL
    current_local_north_m = 0.0
    current_local_east_m = 0.0
    current_local_down_m = 0.0
    local_position_ready = False
    safety_speed_scale = 1.0
    print(
        f"[SAFETY] Sensor default -> "
        f"{'ON' if safety_sensor_enabled else 'OFF'} "
        f"(press o to toggle)",
        flush=True,
    )

    def has_manual_motion() -> bool:
        return (
                current_forward_m_s != 0.0
                or current_right_m_s != 0.0
                or current_down_m_s != 0.0
        )

    def set_motion_owner(owner) -> None:
        nonlocal motion_owner
        if owner != motion_owner:
            print(f"[OWNER] {motion_owner.value} -> {owner.value}", flush=True)
        motion_owner = owner

    def get_motion_owner():
        return motion_owner

    def set_safety_speed_scale(scale: float) -> None:
        nonlocal safety_speed_scale
        safety_speed_scale = max(0.0, min(1.0, scale))

    def update_local_position(north_m: float, east_m: float, down_m: float) -> None:
        nonlocal current_local_north_m
        nonlocal current_local_east_m
        nonlocal current_local_down_m
        nonlocal local_position_ready
        current_local_north_m = north_m
        current_local_east_m = east_m
        current_local_down_m = down_m
        local_position_ready = True

    def launch_pad_clear() -> bool:
        if not local_position_ready:
            return True
        launch_distance = math.hypot(
            current_local_north_m,
            current_local_east_m,
        )
        return launch_distance >= MISSION_LAUNCH_PAD_CLEAR_RADIUS_M

    def force_manual_control() -> None:
        set_motion_owner(MotionOwner.MANUAL)


    def current_saved_motion():
        if not has_manual_motion():
            return None

        return SavedMotion(
            forward_m_s=current_forward_m_s,
            right_m_s=current_right_m_s,
            down_m_s=current_down_m_s,
            north_m_s=current_north_m_s,
            east_m_s=current_east_m_s,
            yaw_deg=current_yaw_deg,
        )
    def stop_manual_motion(clear_saved: bool = True) -> None:
        nonlocal current_forward_m_s
        nonlocal current_right_m_s
        nonlocal current_north_m_s
        nonlocal current_east_m_s
        nonlocal current_down_m_s

        current_forward_m_s = 0.0
        current_right_m_s = 0.0
        current_north_m_s = 0.0
        current_east_m_s = 0.0
        current_down_m_s = 0.0

    def current_mission_debug():
        if active_mission is None or not local_position_ready:
            return {
                "missionCode": "NONE",
                "target": "NONE",
                "position": "UNKNOWN",
                "remaining": "UNKNOWN",
                "altitude": "UNKNOWN",
                "altitude_error": "UNKNOWN",
                "launch_distance": "UNKNOWN",
            }

        target_north = float(active_mission["targetNorthM"])
        target_east = float(active_mission["targetEastM"])
        target_altitude = active_mission.get("targetAltitudeM")
        current_altitude = abs(current_local_down_m)
        horizontal_remaining = math.hypot(
            target_north - current_local_north_m,
            target_east - current_local_east_m,
        )
        launch_distance = math.hypot(
            current_local_north_m - float(active_mission.get("_launch_north_m", current_local_north_m)),
            current_local_east_m - float(active_mission.get("_launch_east_m", current_local_east_m)),
        )

        if target_altitude is None:
            altitude_error = "hold"
            target_altitude_text = "hold"
        else:
            altitude_error = f"{float(target_altitude) - current_altitude:.2f}m"
            target_altitude_text = f"{float(target_altitude):.1f}m"

        return {
            "missionCode": active_mission.get("missionCode", "UNKNOWN"),
            "phase": active_mission.get("_phase", "UNKNOWN"),
            "target": (
                f"N={target_north:.1f} E={target_east:.1f} "
                f"ALT={target_altitude_text}"
            ),
            "position": (
                f"N={current_local_north_m:.1f} "
                f"E={current_local_east_m:.1f} "
                f"D={current_local_down_m:.1f}"
            ),
            "remaining": f"{horizontal_remaining:.1f}m horizontal",
            "altitude": f"{current_altitude:.1f}m",
            "altitude_error": altitude_error,
            "launch_distance": (
                f"{launch_distance:.1f}/"
                f"{MISSION_LAUNCH_PAD_CLEAR_RADIUS_M:.1f}m"
            ),
        }

    # ================================================================
    # START LIDAR / SAFETY
    # ================================================================

    if LidarGateway is None or AvoidanceController is None:
        print(f"[LIDAR] Disabled: {LIDAR_IMPORT_ERROR}")
        print(
            "[LIDAR] Flight control continues without obstacle avoidance."
        )
    else:
        lidar = LidarGateway()
        lidar.start()

        avoidance = AvoidanceController(drone)

        position_task = asyncio.create_task(
            track_local_position(
                connection_manager,
                update_local_position,
            )
        )

        safety_task = asyncio.create_task(
            obstacle_safety_loop(
                avoidance,
                lidar,
                connection_manager,
                lambda: current_yaw_deg,
                current_saved_motion,
                lambda: safety_sensor_enabled,
                get_motion_owner,
                set_motion_owner,
                stop_manual_motion,
                set_safety_speed_scale,
                launch_pad_clear,
            )
        )

    while True:

        key = await asyncio.to_thread(read_key)

        if (
                motion_owner != MotionOwner.MANUAL
                and not safety_sensor_enabled
        ):
            force_manual_control()

        if key in {"w", "a", "s", "d", "f", "v", "q", "e"} and motion_owner != MotionOwner.MANUAL:
            print("[CONTROL] Obstacle stop active - hover until path is clear", flush=True)
            continue

        if key in {"1", "2"}:
            delta = SPEED_ADJUST_STEP_M_S if key == "1" else -SPEED_ADJUST_STEP_M_S
            control_speed_m_s = max(0.0, control_speed_m_s + delta)
            mission_cruise_speed_m_s = max(0.0, mission_cruise_speed_m_s + delta)
            print(
                f"[SPEED] manual={control_speed_m_s:.1f}m/s "
                f"mission={mission_cruise_speed_m_s:.1f}m/s "
                f"step={SPEED_ADJUST_STEP_M_S:.1f}m/s",
                flush=True,
            )

            body_horizontal = math.hypot(current_forward_m_s, current_right_m_s)
            if body_horizontal > 1e-6 and motion_owner == MotionOwner.MANUAL:
                scale = control_speed_m_s / body_horizontal
                current_forward_m_s *= scale
                current_right_m_s *= scale
                current_north_m_s, current_east_m_s = body_velocity(
                    current_forward_m_s,
                    current_right_m_s,
                    current_yaw_deg,
                )
                try:
                    active_drone = await set_motion(
                        connection_manager,
                        current_north_m_s,
                        current_east_m_s,
                        current_down_m_s,
                        current_yaw_deg,
                    )
                    if active_drone is not None and avoidance is not None:
                        avoidance.set_drone(active_drone)
                except OffboardError as exc:
                    print_command_denied("speed adjust", exc)
                except grpc.aio.AioRpcError as exc:
                    print_mavsdk_unavailable("speed adjust", exc)

        elif key == "t":
            print("[CMD] arm + takeoff")
            active_drone = await safe_arm(connection_manager)
            if active_drone is not None:
                if avoidance is not None:
                    avoidance.set_drone(active_drone)
                try:
                    await active_drone.action.takeoff()
                    print("[CMD] Takeoff command sent - climbing...")
                    await asyncio.sleep(5)
                    print("[CMD] Takeoff complete. Use wasdqefv to start offboard flight, k to hover.")
                except (ActionError, OffboardError) as exc:
                    print_command_denied("takeoff/offboard", exc)
                except grpc.aio.AioRpcError as exc:
                    print_mavsdk_unavailable("takeoff", exc)
                    if is_grpc_unavailable(exc):
                        new_drone = await connection_manager.reconnect()
                        if new_drone is not None and avoidance is not None:
                            avoidance.set_drone(new_drone)
                        if new_drone is not None:
                            try:
                                await new_drone.action.takeoff()
                                print("[CMD] Takeoff command sent - climbing...")
                                await asyncio.sleep(5)
                                print("[CMD] Takeoff complete. Use wasdqefv to start offboard flight, k to hover.")
                            except (ActionError, OffboardError) as retry_exc:
                                print_command_denied("takeoff/offboard", retry_exc)
                            except grpc.aio.AioRpcError as retry_exc:
                                print_mavsdk_unavailable("takeoff retry", retry_exc)
        elif key == "w":
            print("[CMD] forward")
            current_forward_m_s = control_speed_m_s
            current_right_m_s = 0.0
            current_north_m_s, current_east_m_s = body_velocity(
                current_forward_m_s, current_right_m_s, current_yaw_deg
            )
            current_down_m_s = 0.0
            try:
                active_drone = await set_motion(connection_manager, current_north_m_s, current_east_m_s, current_down_m_s, current_yaw_deg)
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("forward", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("forward", exc)
        elif key == "s":
            print("[CMD] backward")
            current_forward_m_s = -control_speed_m_s
            current_right_m_s = 0.0
            current_north_m_s, current_east_m_s = body_velocity(
                current_forward_m_s, current_right_m_s, current_yaw_deg
            )
            current_down_m_s = 0.0
            try:
                active_drone = await set_motion(connection_manager, current_north_m_s, current_east_m_s, current_down_m_s, current_yaw_deg)
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("backward", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("backward", exc)
        elif key == "a":
            print("[CMD] left")
            current_forward_m_s = 0.0
            current_right_m_s = -control_speed_m_s
            current_north_m_s, current_east_m_s = body_velocity(
                current_forward_m_s, current_right_m_s, current_yaw_deg
            )
            current_down_m_s = 0.0
            try:
                active_drone = await set_motion(connection_manager, current_north_m_s, current_east_m_s, current_down_m_s, current_yaw_deg)
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("left", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("left", exc)
        elif key == "d":
            print("[CMD] right")
            current_forward_m_s = 0.0
            current_right_m_s = control_speed_m_s
            current_north_m_s, current_east_m_s = body_velocity(
                current_forward_m_s, current_right_m_s, current_yaw_deg
            )
            current_down_m_s = 0.0
            try:
                active_drone = await set_motion(connection_manager, current_north_m_s, current_east_m_s, current_down_m_s, current_yaw_deg)
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("right", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("right", exc)
        elif key == "f":
            print("[CMD] up")
            current_down_m_s = -VERTICAL_SPEED_M_S
            try:
                active_drone = await set_motion(connection_manager, current_north_m_s, current_east_m_s, current_down_m_s, current_yaw_deg)
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("up", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("up", exc)
        elif key == "v":
            print("[CMD] down")
            current_down_m_s = VERTICAL_SPEED_M_S
            try:
                active_drone = await set_motion(connection_manager, current_north_m_s, current_east_m_s, current_down_m_s, current_yaw_deg)
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("down", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("down", exc)
        elif key == "q":
            current_yaw_deg = (current_yaw_deg - YAW_STEP_DEG) % 360.0
            print(f"[CMD] yaw left -> {current_yaw_deg:.0f} deg")
            current_north_m_s, current_east_m_s = body_velocity(
                current_forward_m_s, current_right_m_s, current_yaw_deg
            )

            try:
                active_drone = await set_motion(
                    connection_manager,
                    current_north_m_s,
                    current_east_m_s,
                    current_down_m_s,
                    current_yaw_deg
                )
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("yaw left", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("yaw left", exc)

        elif key == "e":
            current_yaw_deg = (current_yaw_deg + YAW_STEP_DEG) % 360.0
            print(f"[CMD] yaw right -> {current_yaw_deg:.0f} deg")
            current_north_m_s, current_east_m_s = body_velocity(
                current_forward_m_s, current_right_m_s, current_yaw_deg
            )

            try:
                active_drone = await set_motion(
                    connection_manager,
                    current_north_m_s,
                    current_east_m_s,
                    current_down_m_s,
                    current_yaw_deg
                )
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("yaw right", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("yaw right", exc)
        elif key in ("k", "h"):
            print("[CMD] stop / hover")
            active_mission = None
            set_motion_owner(MotionOwner.MANUAL)
            stop_manual_motion()
            try:
                active_drone = await set_motion(connection_manager, 0.0, 0.0, 0.0, current_yaw_deg)
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("stop/hover", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("stop/hover", exc)
        elif key == "o":
            safety_sensor_enabled = not safety_sensor_enabled
            if not safety_sensor_enabled and motion_owner == MotionOwner.EMERGENCY:
                force_manual_control()
            state = "ON" if safety_sensor_enabled else "OFF"
            print(f"[SAFETY] Sensor toggle -> {state}", flush=True)
        elif key == "p":
            task = asyncio.create_task(camera.capture_and_upload())
            task.add_done_callback(
                lambda done: print(f"[CAMERA] Background error: {done.exception()}")
                if done.exception()
                else None
            )
        elif key == "l":
            print("[CMD] land")
            active_drone = await connection_manager.get_drone()
            if active_drone is None:
                active_drone = await connection_manager.reconnect()
            if active_drone is None:
                print("[ERR] MAVSDK control bridge unavailable")
                continue
            try:
                await active_drone.offboard.stop()
            except OffboardError:
                pass
            try:
                await active_drone.action.land()
            except ActionError as exc:
                print_command_denied("land", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("land", exc)
                if is_grpc_unavailable(exc):
                    new_drone = await connection_manager.reconnect()
                    if new_drone is not None and avoidance is not None:
                        avoidance.set_drone(new_drone)
                    if new_drone is not None:
                        try:
                            await new_drone.action.land()
                        except ActionError as retry_exc:
                            print_command_denied("land", retry_exc)
                        except grpc.aio.AioRpcError as retry_exc:
                            print_mavsdk_unavailable("land retry", retry_exc)
        elif key == "x":
            print("[SHUTDOWN] Stopped by user")
            return


if __name__ == "__main__":
    configure_mavsdk_logging()
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[SHUTDOWN] Stopped by user")
