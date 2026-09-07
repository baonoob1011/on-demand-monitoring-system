import asyncio
from datetime import datetime, timezone
from io import BytesIO
import logging
import math
import os
import socket
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
ENV_FILE = Path(
    "/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/drone-controller/.env.example"
)

load_dotenv(ENV_FILE)

print(
    f"[ENV] Loaded: {ENV_FILE}",
    flush=True,
)

print(
    f"[ENV] OpenRouter key loaded: {'YES' if os.getenv('OPENROUTER_API_KEY') else 'NO'}",
    flush=True,
)
try:
    from obstacle_avoidance.ai_decision_client import AiDecisionClient
    from obstacle_avoidance.lidar_gateway import LidarGateway
    from obstacle_avoidance.avoidance_controller import AvoidanceController
    from obstacle_avoidance.sensor_reader import OBSTACLE_DISTANCE_M, front_obstacle_reading
except ImportError as exc:
    AiDecisionClient = None
    LidarGateway = None
    AvoidanceController = None
    OBSTACLE_DISTANCE_M = 5.0
    front_obstacle_reading = None
    LIDAR_IMPORT_ERROR = exc
else:
    LIDAR_IMPORT_ERROR = None

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
MAVSDK_CONTROL_GRPC_PORT = int(os.getenv("MAVSDK_CONTROL_GRPC_PORT", "50052"))
MAVSDK_CONTROL_SYSID = int(os.getenv("MAVSDK_CONTROL_SYSID", "245"))
MAVSDK_CONTROL_COMPID = int(os.getenv("MAVSDK_CONTROL_COMPID", "191"))

MOVE_SPEED_M_S = float(os.getenv("CONTROL_MOVE_SPEED_M_S", "10.0"))
VERTICAL_SPEED_M_S = float(os.getenv("CONTROL_VERTICAL_SPEED_M_S", "500.0"))
YAW_STEP_DEG = float(os.getenv("CONTROL_YAW_STEP_DEG", "30.0"))
AI_PREFER_UP = os.getenv("AI_PREFER_UP", "true").strip().lower() == "true"
AI_ALLOW_UNVERIFIED_VERTICAL = os.getenv("AI_ALLOW_UNVERIFIED_VERTICAL", "true").strip().lower() == "true"
AI_MAX_AVOIDANCE_ALTITUDE_M = float(os.getenv("AI_MAX_AVOIDANCE_ALTITUDE_M", "25.0"))
AI_MIN_AVOIDANCE_ALTITUDE_M = float(os.getenv("AI_MIN_AVOIDANCE_ALTITUDE_M", "5.0"))
AI_HORIZONTAL_SPEED_M_S = float(os.getenv("AI_HORIZONTAL_SPEED_M_S", "3.0"))
AI_VERTICAL_SPEED_M_S = float(os.getenv("AI_VERTICAL_SPEED_M_S", "10.0"))
AI_MOVE_DURATION_S = float(os.getenv("AI_MOVE_DURATION_S", "1.0"))
AI_UP_STEP_DURATION_S = float(os.getenv("AI_UP_STEP_DURATION_S", "1.0"))
AI_DOWN_STEP_DURATION_S = float(os.getenv("AI_DOWN_STEP_DURATION_S", "1.0"))
AI_YAW_STEP_DEG = float(os.getenv("AI_YAW_STEP_DEG", "30.0"))
AI_SETTLE_S = float(os.getenv("AI_SETTLE_S", "1.0"))
SAFETY_POLL_INTERVAL_S = float(os.getenv("SAFETY_POLL_INTERVAL_S", "0.5"))
AI_MIN_CLEARANCE_IMPROVEMENT_M = float(os.getenv("AI_MIN_CLEARANCE_IMPROVEMENT_M", "0.5"))
AI_MAX_SAME_ACTION_REPEATS = int(os.getenv("AI_MAX_SAME_ACTION_REPEATS", "2"))
AI_MAX_AVOIDANCE_ATTEMPTS = int(os.getenv("AI_MAX_AVOIDANCE_ATTEMPTS", "15"))
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
DEVICE_CODE = os.getenv("DEVICE_CODE", "DRONE-01")
DRONE_ID = os.getenv("DRONE_ID", DEVICE_CODE)
MISSION_ID = os.getenv("MISSION_ID", "MISSION_001")
SIM_WORLD = os.getenv("SIM_WORLD", "legacy")
DEFAULT_GAZEBO_WORLD = "forest_monitoring_compact" if SIM_WORLD == "compact" else "forest_monitoring"
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
        north: float,
        east: float,
        down: float,
        yaw_deg: float = 0.0,
) -> System | None:
    drone = await manager.get_drone()
    if drone is None:
        drone = await manager.reconnect()
        if drone is None:
            print("[ERR] MAVSDK control bridge unavailable")
            return None

    if not await check_readiness(drone):
        drone = await manager.reconnect()
        if drone is None:
            print("[ERR] MAVSDK control bridge unavailable")
            return None

    try:
        await ensure_offboard_started(drone)
        await drone.offboard.set_velocity_ned(VelocityNedYaw(north, east, down, yaw_deg))
        return drone
    except grpc.aio.AioRpcError as exc:
        if not is_grpc_unavailable(exc):
            raise

        print_mavsdk_unavailable("movement", exc)
        drone = await manager.reconnect()
        if drone is None:
            print("[ERR] MAVSDK control bridge unavailable")
            return None

        try:
            await ensure_offboard_started(drone)
            await drone.offboard.set_velocity_ned(VelocityNedYaw(north, east, down, yaw_deg))
            return drone
        except grpc.aio.AioRpcError as retry_exc:
            if not is_grpc_unavailable(retry_exc):
                raise

            print("[WARN] movement retry skipped: MAVSDK still unavailable")
            return None


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


def get_clearance(state, action: str) -> float:
    if action == "LEFT":
        return state.left
    if action == "RIGHT":
        return state.right
    if action == "BACK":
        return state.back
    return state.nearest_distance


def is_forward_blocked(state) -> bool:
    return state.front <= OBSTACLE_DISTANCE_M


def is_front_obstacle_direction(direction: str) -> bool:
    return direction == "FRONT"


def should_handle_front_obstacle(state, direction: str) -> bool:
    return is_forward_blocked(state) and is_front_obstacle_direction(direction)


def choose_safer_lateral_direction(state) -> str | None:
    left_clear = state.left > OBSTACLE_DISTANCE_M
    right_clear = state.right > OBSTACLE_DISTANCE_M

    if left_clear and right_clear:
        return "LEFT" if state.left >= state.right else "RIGHT"
    if left_clear:
        return "LEFT"
    if right_clear:
        return "RIGHT"
    return None


def vertical_allowed(action: str) -> bool:
    if action in ("UP", "DOWN") and not AI_ALLOW_UNVERIFIED_VERTICAL:
        return False
    return True


def choose_safe_fallback(state, altitude_m: float, up_ineffective: bool) -> str:
    if (
        AI_PREFER_UP
        and not up_ineffective
        and altitude_m < AI_MAX_AVOIDANCE_ALTITUDE_M
        and vertical_allowed("UP")
    ):
        return "UP"

    lateral = choose_safer_lateral_direction(state)
    if lateral is not None:
        return lateral

    if state.back > OBSTACLE_DISTANCE_M:
        return "BACK"

    if altitude_m > AI_MIN_AVOIDANCE_ALTITUDE_M and vertical_allowed("DOWN"):
        return "DOWN"

    return "HOVER"


def should_climb_before_ai(state, altitude_m: float, up_ineffective: bool) -> bool:
    return (
        AI_PREFER_UP
        and is_forward_blocked(state)
        and not up_ineffective
        and altitude_m < AI_MAX_AVOIDANCE_ALTITUDE_M
        and vertical_allowed("UP")
    )


def direction_angle(state, direction: str) -> float:
    if direction == "FRONT":
        return state.front_angle
    if direction == "FRONT_LEFT":
        return state.front_left_angle
    if direction == "FRONT_RIGHT":
        return state.front_right_angle
    if direction == "LEFT":
        return state.left_angle
    if direction == "RIGHT":
        return state.right_angle
    if direction == "BACK_LEFT":
        return state.back_left_angle
    if direction == "BACK_RIGHT":
        return state.back_right_angle
    if direction == "BACK":
        return state.back_angle
    return state.nearest_angle


def is_oscillation(action: str, recent_actions: list[str]) -> bool:
    if not recent_actions:
        return False

    previous = recent_actions[-1]
    return (previous, action) in {
        ("LEFT", "RIGHT"),
        ("RIGHT", "LEFT"),
        ("YAW_LEFT", "YAW_RIGHT"),
        ("YAW_RIGHT", "YAW_LEFT"),
    }
def validate_ai_action(
        action: str,
        state,
        altitude_m: float,
        up_ineffective: bool,
        recent_actions: list[str],
) -> tuple[bool, str]:

    if action == "HOVER":
        return True, "always allowed"

    if action == "UP":
        if not vertical_allowed(action):
            return False, "unverified vertical clearance disabled"

        if altitude_m >= AI_MAX_AVOIDANCE_ALTITUDE_M:
            return False, "altitude limit"

        if up_ineffective:
            return False, "UP temporarily ineffective"

        return True, "approved"

    if action == "LEFT":
        if min(
                state.left,
                state.front_left,
        ) <= OBSTACLE_DISTANCE_M:
            return False, "LEFT path blocked"

        if is_oscillation(action, recent_actions):
            return False, "oscillation rejected"

        return True, "approved"

    if action == "RIGHT":
        if min(
                state.right,
                state.front_right,
        ) <= OBSTACLE_DISTANCE_M:
            return False, "RIGHT path blocked"

        if is_oscillation(action, recent_actions):
            return False, "oscillation rejected"

        return True, "approved"

    if action == "BACK":
        if min(
                state.back,
                state.back_left,
                state.back_right,
        ) <= OBSTACLE_DISTANCE_M:
            return False, "BACK path blocked"

        return True, "approved"

    if action == "DOWN":
        if not vertical_allowed(action):
            return False, "unverified vertical clearance disabled"

        if altitude_m <= AI_MIN_AVOIDANCE_ALTITUDE_M:
            return False, "minimum altitude limit"

        return True, "approved"

    if action in ("YAW_LEFT", "YAW_RIGHT"):
        if is_oscillation(action, recent_actions):
            return False, "oscillation rejected"

        return True, "approved"

    return False, "invalid action"

async def get_current_altitude(manager: MavsdkConnectionManager) -> float:
    drone = await manager.get_drone()
    if drone is None:
        return 0.0

    try:
        async with asyncio.timeout(2.0):
            async for position in drone.telemetry.position():
                return float(position.relative_altitude_m)
    except (asyncio.TimeoutError, grpc.aio.AioRpcError):
        return 0.0

    return 0.0


def build_ai_state(state, status: str, altitude_m: float, yaw_deg: float, previous_action: str, attempt: int, up_ineffective: bool) -> dict:
    if front_obstacle_reading is not None:
        _front_status, nearest_direction, nearest_distance_m = front_obstacle_reading(state)
    else:
        nearest_direction = state.nearest_direction
        nearest_distance_m = state.nearest_distance

    return {
        "status": status,
        "nearest_direction": nearest_direction,
        "nearest_distance_m": nearest_distance_m,
        "nearest_angle_deg": direction_angle(state, nearest_direction),
        "front_m": state.front,
        "front_left_m": state.front_left,
        "front_right_m": state.front_right,
        "left_m": state.left,
        "right_m": state.right,
        "back_left_m": state.back_left,
        "back_m": state.back,
        "back_right_m": state.back_right,
        "altitude_m": altitude_m,
        "min_altitude_m": AI_MIN_AVOIDANCE_ALTITUDE_M,
        "max_altitude_m": AI_MAX_AVOIDANCE_ALTITUDE_M,
        "yaw_deg": yaw_deg,
        "goal_direction": "FORWARD",
        "previous_action": previous_action,
        "avoidance_attempt": attempt,
        "prefer_up": AI_PREFER_UP,
        "vertical_allowed": AI_ALLOW_UNVERIFIED_VERTICAL,
        "up_temporarily_ineffective": up_ineffective,
        "obstacle_threshold_m": OBSTACLE_DISTANCE_M,
    }


def log_ai_state(ai_state: dict) -> None:
    print(
        "[AI-STATE]\n"
        f"STATUS        : {ai_state['status']}\n"
        f"NEAREST       : {ai_state['nearest_direction']} @ {ai_state['nearest_distance_m']:.2f}m\n"
        f"FRONT         : {ai_state['front_m']:.2f}m\n"
        f"FRONT_LEFT    : {ai_state['front_left_m']:.2f}m\n"
        f"FRONT_RIGHT   : {ai_state['front_right_m']:.2f}m\n"
        f"LEFT          : {ai_state['left_m']:.2f}m\n"
        f"RIGHT         : {ai_state['right_m']:.2f}m\n"
        f"BACK          : {ai_state['back_m']:.2f}m\n"
        f"ALTITUDE      : {ai_state['altitude_m']:.2f}m\n"
        f"ALTITUDE MAX  : {ai_state['max_altitude_m']:.2f}m\n"
        f"YAW           : {ai_state['yaw_deg']:.2f}deg\n"
        f"ATTEMPT       : {ai_state['avoidance_attempt']}\n"
        f"PREVIOUS      : {ai_state['previous_action']}",
        flush=True,
    )


async def execute_ai_action(
        action: str,
        manager: MavsdkConnectionManager,
        avoidance,
        yaw_deg: float,
        set_yaw,
) -> tuple[System | None, float]:
    print("[AI-EXEC]", flush=True)

    if action == "UP":
        print(
            f"ACTION    : UP\nSPEED     : {AI_VERTICAL_SPEED_M_S:.2f}m/s\nDURATION  : {AI_UP_STEP_DURATION_S:.2f}s",
            flush=True,
        )
        drone = await set_motion(manager, 0.0, 0.0, -AI_VERTICAL_SPEED_M_S, yaw_deg)
        await asyncio.sleep(AI_UP_STEP_DURATION_S)
    elif action == "DOWN":
        print(
            f"ACTION    : DOWN\nSPEED     : {AI_VERTICAL_SPEED_M_S:.2f}m/s\nDURATION  : {AI_DOWN_STEP_DURATION_S:.2f}s",
            flush=True,
        )
        drone = await set_motion(manager, 0.0, 0.0, AI_VERTICAL_SPEED_M_S, yaw_deg)
        await asyncio.sleep(AI_DOWN_STEP_DURATION_S)
    elif action == "LEFT":
        north, east = body_velocity(0.0, -AI_HORIZONTAL_SPEED_M_S, yaw_deg)
        print(
            f"ACTION    : LEFT\nSPEED     : {AI_HORIZONTAL_SPEED_M_S:.2f}m/s\nDURATION  : {AI_MOVE_DURATION_S:.2f}s",
            flush=True,
        )
        drone = await set_motion(manager, north, east, 0.0, yaw_deg)
        await asyncio.sleep(AI_MOVE_DURATION_S)
    elif action == "RIGHT":
        north, east = body_velocity(0.0, AI_HORIZONTAL_SPEED_M_S, yaw_deg)
        print(
            f"ACTION    : RIGHT\nSPEED     : {AI_HORIZONTAL_SPEED_M_S:.2f}m/s\nDURATION  : {AI_MOVE_DURATION_S:.2f}s",
            flush=True,
        )
        drone = await set_motion(manager, north, east, 0.0, yaw_deg)
        await asyncio.sleep(AI_MOVE_DURATION_S)
    elif action == "BACK":
        north, east = body_velocity(-AI_HORIZONTAL_SPEED_M_S, 0.0, yaw_deg)
        print(
            f"ACTION    : BACK\nSPEED     : {AI_HORIZONTAL_SPEED_M_S:.2f}m/s\nDURATION  : {AI_MOVE_DURATION_S:.2f}s",
            flush=True,
        )
        drone = await set_motion(manager, north, east, 0.0, yaw_deg)
        await asyncio.sleep(AI_MOVE_DURATION_S)
    elif action == "YAW_LEFT":
        yaw_deg = (yaw_deg - AI_YAW_STEP_DEG) % 360.0
        set_yaw(yaw_deg)
        print(f"ACTION    : YAW_LEFT\nYAW STEP  : {AI_YAW_STEP_DEG:.2f}deg", flush=True)
        drone = await set_motion(manager, 0.0, 0.0, 0.0, yaw_deg)
    elif action == "YAW_RIGHT":
        yaw_deg = (yaw_deg + AI_YAW_STEP_DEG) % 360.0
        set_yaw(yaw_deg)
        print(f"ACTION    : YAW_RIGHT\nYAW STEP  : {AI_YAW_STEP_DEG:.2f}deg", flush=True)
        drone = await set_motion(manager, 0.0, 0.0, 0.0, yaw_deg)
    else:
        drone = await set_motion(manager, 0.0, 0.0, 0.0, yaw_deg)

    if drone is not None:
        avoidance.set_drone(drone)
        print("[AI-EXEC] HOVER", flush=True)
        hover_drone = await set_motion(manager, 0.0, 0.0, 0.0, yaw_deg)
        if hover_drone is not None:
            avoidance.set_drone(hover_drone)

    await asyncio.sleep(AI_SETTLE_S)
    return drone, yaw_deg


async def start_ai_avoidance(
        initial_state,
        lidar,
        manager: MavsdkConnectionManager,
        avoidance,
        get_yaw,
        set_yaw,
        resume_motion,
) -> None:

    if AiDecisionClient is None:
        print(
            "[AI] Decision client unavailable - HOVER",
            flush=True,
        )
        return

    client = AiDecisionClient()

    recent_actions: list[str] = []
    rejected_actions: list[str] = []

    previous_action = "NONE"
    last_rejection_reason = "NONE"
    has_executed_ai_action = False
    up_repeat_count = 0
    up_baseline_front = initial_state.front
    up_ineffective = False

    # ================================================================
    # DRONE HAS JUST BEEN STOPPED
    # Wait for momentum / deceleration before first AI scan.
    # ================================================================

    print(
        f"[AI-AVOID] Stabilizing for {AI_SETTLE_S:.2f}s before scan...",
        flush=True,
    )

    await asyncio.sleep(AI_SETTLE_S)

    # ================================================================
    # AI DECISION LOOP
    # ================================================================

    for attempt in range(
            1,
            AI_MAX_AVOIDANCE_ATTEMPTS + 1,
    ):

        # ------------------------------------------------------------
        # FRESH LIDAR SCAN
        # ------------------------------------------------------------

        state, status, direction = lidar.snapshot()

        if state is None:
            print(
                "[AI-AVOID] No LiDAR state -> HOVER",
                flush=True,
            )

            await set_motion(
                manager,
                0.0,
                0.0,
                0.0,
                get_yaw(),
            )

            return

        # ------------------------------------------------------------
        # CHECK IF ORIGINAL FORWARD PATH IS NOW CLEAR
        # ------------------------------------------------------------

        if has_executed_ai_action and not should_handle_front_obstacle(state, direction):
            print(
                "[AI-AVOID] Forward path CLEAR",
                flush=True,
            )

            print(
                "[AI-AVOID] Avoidance successful -> RESUME previous flight",
                flush=True,
            )

            await resume_motion()

            return

        if not has_executed_ai_action and not should_handle_front_obstacle(state, direction):
            print(
                "[AI-AVOID] No front obstacle -> no avoidance needed",
                flush=True,
            )

            return
        # ------------------------------------------------------------
        # FLIGHT STATE
        # ------------------------------------------------------------

        altitude_m = await get_current_altitude(
            manager
        )

        yaw_deg = get_yaw()

        ai_state = build_ai_state(
            state,
            status,
            altitude_m,
            yaw_deg,
            previous_action,
            attempt,
            up_ineffective,
        )

        # Give AI its own action history.
        ai_state["recent_actions"] = recent_actions[-4:]
        ai_state["rejected_actions"] = rejected_actions[-4:]

        ai_state["last_rejection_reason"] = (
            last_rejection_reason
        )

        log_ai_state(ai_state)

        if not should_handle_front_obstacle(state, direction):
            print(
                "[AI-AVOID] Obstacle is not in front -> skip AI",
                flush=True,
            )

            return

        if should_climb_before_ai(
                state,
                altitude_m,
                up_ineffective,
        ):
            requested_action = "UP"

            print(
                "[AI-AVOID] Forward obstacle detected -> local priority climb UP",
                flush=True,
            )

        else:
            # ------------------------------------------------------------
            # AI DECIDES -> SEND COMMAND TO FLIGHT SCRIPT
            # ------------------------------------------------------------

            print(
                "[AI-AVOID] UP unavailable/ineffective -> Sending LiDAR state to AI...",
                flush=True,
            )

            requested_action = await client.choose_action(ai_state)

        print(
            "[AI-COMMAND] ========================================\n"
            f"[AI-COMMAND] DECISION : {requested_action}\n"
            "[AI-COMMAND] Sending decision to flight executor...\n"
            "[AI-COMMAND] ========================================",
            flush=True,
        )
        # ------------------------------------------------------------
        # LOCAL SAFETY GUARD
        # ------------------------------------------------------------

        approved, reason = validate_ai_action(
            requested_action,
            state,
            altitude_m,
            up_ineffective,
            recent_actions,
        )

        if not approved:
            print(
                "[AI-SAFETY]\n"
                f"REQUESTED : {requested_action}\n"
                "RESULT    : REJECTED\n"
                f"REASON    : {reason}",
                flush=True,
            )

            # IMPORTANT:
            # Local code does NOT select LEFT/RIGHT/UP/etc.
            # It only stops the drone.
            await set_motion(
                manager,
                0.0,
                0.0,
                0.0,
                yaw_deg,
            )

            rejected_actions.append(
                requested_action
            )

            rejected_actions = (
                rejected_actions[-4:]
            )

            previous_action = requested_action
            last_rejection_reason = reason

            print(
                "[AI-AVOID] Rejected action -> HOVER -> RESCAN -> ASK AI AGAIN",
                flush=True,
            )

            await asyncio.sleep(
                AI_SETTLE_S
            )

            continue

        print(
            "[AI-SAFETY]\n"
            f"REQUESTED : {requested_action}\n"
            "RESULT    : APPROVED",
            flush=True,
        )

        if (
                requested_action == "UP"
                and AI_ALLOW_UNVERIFIED_VERTICAL
        ):
            print(
                "[AI-SAFETY] WARNING: "
                "UP vertical clearance is unverified",
                flush=True,
            )

        # ------------------------------------------------------------
        # AI CHOSE HOVER
        # ------------------------------------------------------------

        if requested_action == "HOVER":
            await set_motion(
                manager,
                0.0,
                0.0,
                0.0,
                yaw_deg,
            )

            print(
                "[AI-AVOID] AI chose HOVER",
                flush=True,
            )

            await asyncio.sleep(
                AI_SETTLE_S
            )

            continue

        # ------------------------------------------------------------
        # EXECUTE AI ACTION
        # ------------------------------------------------------------

        before_front = min(
            state.front,
            state.front_left,
            state.front_right,
        )

        _drone, yaw_deg = await execute_ai_action(
            requested_action,
            manager,
            avoidance,
            yaw_deg,
            set_yaw,
        )
        has_executed_ai_action = True
        recent_actions.append(
            requested_action
        )

        recent_actions = recent_actions[-4:]

        previous_action = requested_action
        last_rejection_reason = "NONE"

        # ------------------------------------------------------------
        # FRESH SCAN AFTER MOVEMENT
        # ------------------------------------------------------------

        print(
            "[AI-AVOID] Movement complete",
            flush=True,
        )
        print(
            "[AI-AVOID] HOVER -> RESCAN",
            flush=True,
        )

        await asyncio.sleep(
            AI_SETTLE_S
        )

        fresh_state, fresh_status, _ = (
            lidar.snapshot()
        )

        if fresh_state is None:
            continue

        after_front = min(
            fresh_state.front,
            fresh_state.front_left,
            fresh_state.front_right,
        )
        fresh_altitude_m = await get_current_altitude(
            manager
        )

        improvement = (
                after_front - before_front
        )

        # ------------------------------------------------------------
        # TRACK WHETHER UP ACTUALLY HELPED
        # ------------------------------------------------------------

        if requested_action == "UP":
            up_repeat_count += 1

            if not is_forward_blocked(fresh_state):
                print(
                    "[AI-AVOID] UP cleared forward path "
                    f"at altitude {fresh_altitude_m:.2f}m",
                    flush=True,
                )

                up_repeat_count = 0
                up_baseline_front = after_front

            elif fresh_altitude_m < AI_MAX_AVOIDANCE_ALTITUDE_M:
                print(
                    "[AI-AVOID] Forward still blocked after UP "
                    f"(altitude={fresh_altitude_m:.2f}m) -> keep climbing",
                    flush=True,
                )

            elif improvement >= AI_MIN_CLEARANCE_IMPROVEMENT_M:
                print(
                    "[AI-AVOID] UP improved clearance "
                    f"by {improvement:.2f}m",
                    flush=True,
                )

                up_repeat_count = 0
                up_baseline_front = after_front

            elif up_repeat_count >= AI_MAX_SAME_ACTION_REPEATS:
                print(
                    "[AI-AVOID] UP ineffective "
                    f"(improvement={improvement:.2f}m)",
                    flush=True,
                )

                up_ineffective = True

        else:
            up_repeat_count = 0

        print(
            "[AI-AVOID] Feeding new scan back to AI...",
            flush=True,
        )

    # ================================================================
    # MAX ATTEMPTS
    # ================================================================

    print(
        "[AI-AVOID] Maximum attempts reached -> HOVER",
        flush=True,
    )

    await set_motion(
        manager,
        0.0,
        0.0,
        0.0,
        get_yaw(),
    )

async def obstacle_safety_loop(
        avoidance,
        lidar,
        manager: MavsdkConnectionManager,
        get_yaw,
        set_yaw,
        has_manual_motion,
        clear_manual_motion,
        is_safety_sensor_enabled,
        is_ai_obstacle_enabled,
        start_ai_avoidance,
        resume_motion,
):
    last_status = "CLEAR"
    avoidance_active = False
    avoidance_task = None
    unavailable_reported = False
    ready_reported = False
    mavsdk_unavailable_reported = False

    while True:
        if not is_safety_sensor_enabled():
            avoidance_active = False
            last_status = "CLEAR"
            await asyncio.sleep(SAFETY_POLL_INTERVAL_S)
            continue

        state, status, direction = lidar.snapshot()

        if state is None:
            if not getattr(lidar, "available", False) and not unavailable_reported:
                print("[SAFETY] Obstacle avoidance unavailable")
                unavailable_reported = True

            await asyncio.sleep(1.0)
            continue

        if not ready_reported:
            print("[SAFETY] Obstacle avoidance ready")
            ready_reported = True

        if avoidance_task is not None and avoidance_task.done():
            avoidance_task = None
            avoidance_active = False

        front_danger = (
            status in ("OBSTACLE", "EMERGENCY")
            and should_handle_front_obstacle(state, direction)
            and avoidance_task is None
        )

        should_stop_now = front_danger and not avoidance_active

        if should_stop_now:
            avoidance_active = True
            avoidance.set_yaw(get_yaw())
            clear_manual_motion()

            try:
                new_drone = await set_motion(manager, 0.0, 0.0, 0.0, get_yaw())
                if new_drone is not None:
                    avoidance.set_drone(new_drone)
                    await avoidance.hover(log=True)

                if mavsdk_unavailable_reported:
                    print("[SAFETY] MAVSDK control restored")
                    mavsdk_unavailable_reported = False

                if not is_ai_obstacle_enabled():
                    print(
                        f"[SAFETY] {status} detected "
                        f"direction={direction} "
                        f"front={state.front:.2f}m "
                        f"left={state.left:.2f}m "
                        f"right={state.right:.2f}m "
                        "-> STOP / HOVER (AI avoidance OFF)",
                        flush=True,
                    )
                else:
                    print(
                        f"[SAFETY] {status} detected "
                        f"direction={direction} "
                        f"front={state.front:.2f}m "
                        f"left={state.left:.2f}m "
                        f"right={state.right:.2f}m "
                        "-> HOVER -> START AI AVOIDANCE",
                        flush=True,
                    )
                    avoidance_task = asyncio.create_task(
                        start_ai_avoidance(
                            state,
                            lidar,
                            manager,
                            avoidance,
                            get_yaw,
                            set_yaw,
                            resume_motion,
                        )
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

        if status == "CLEAR" and avoidance_task is None:
            avoidance_active = False

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
    print("      o toggle safety sensor | i toggle AI obstacle avoidance")
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

    safety_task = None
    avoidance = None
    lidar = None
    safety_sensor_enabled = False
    ai_obstacle_enabled = False
    print("[SAFETY] Sensor default -> OFF (press o to enable)", flush=True)
    print("[AI] Obstacle avoidance default -> OFF (press i to enable)", flush=True)

    def has_manual_motion() -> bool:
        return (
                current_forward_m_s != 0.0
                or current_right_m_s != 0.0
                or current_down_m_s != 0.0
        )

    def clear_manual_motion() -> None:
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

    async def resume_previous_motion():
        nonlocal current_forward_m_s
        nonlocal current_right_m_s
        nonlocal current_north_m_s
        nonlocal current_east_m_s
        nonlocal current_down_m_s
        nonlocal current_yaw_deg

        if (
                current_forward_m_s == 0.0
                and current_right_m_s == 0.0
                and current_down_m_s == 0.0
        ):
            print(
                "[RESUME] No previous movement -> remain HOVER",
                flush=True,
            )
            return

        current_north_m_s, current_east_m_s = body_velocity(
            current_forward_m_s,
            current_right_m_s,
            current_yaw_deg,
        )

        print(
            "[RESUME] ========================================\n"
            "[RESUME] Avoidance complete -> resume movement\n"
            f"[RESUME] FORWARD : {current_forward_m_s:.2f} m/s\n"
            f"[RESUME] RIGHT   : {current_right_m_s:.2f} m/s\n"
            f"[RESUME] DOWN    : {current_down_m_s:.2f} m/s\n"
            f"[RESUME] YAW     : {current_yaw_deg:.2f} deg\n"
            "[RESUME] ========================================",
            flush=True,
        )

        active_drone = await set_motion(
            connection_manager,
            current_north_m_s,
            current_east_m_s,
            current_down_m_s,
            current_yaw_deg,
        )

        if active_drone is not None and avoidance is not None:
            avoidance.set_drone(active_drone)

        print(
            "[RESUME] Previous movement resumed",
            flush=True,
        )

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

        safety_task = asyncio.create_task(
            obstacle_safety_loop(
                avoidance,
                lidar,
                connection_manager,
                lambda: current_yaw_deg,
                set_current_yaw,
                has_manual_motion,
                clear_manual_motion,
                lambda: safety_sensor_enabled,
                lambda: ai_obstacle_enabled,
                start_ai_avoidance,
                resume_previous_motion,
            )
        )


    while True:

        key = await asyncio.to_thread(read_key)

        if key == "t":
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
            current_forward_m_s = MOVE_SPEED_M_S
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
            current_forward_m_s = -MOVE_SPEED_M_S
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
            current_right_m_s = -MOVE_SPEED_M_S
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
            current_right_m_s = MOVE_SPEED_M_S
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
            current_forward_m_s = 0.0
            current_right_m_s = 0.0
            current_north_m_s = 0.0
            current_east_m_s = 0.0
            current_down_m_s = 0.0
            try:
                active_drone = await set_motion(connection_manager, 0.0, 0.0, 0.0, current_yaw_deg)
                if active_drone is not None and avoidance is not None:
                    avoidance.set_drone(active_drone)
            except OffboardError as exc:
                print_command_denied("stop/hover", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("stop/hover", exc)
        elif key == "i":
            ai_obstacle_enabled = not ai_obstacle_enabled
            state = "ON" if ai_obstacle_enabled else "OFF"
            print(f"[AI] Obstacle avoidance toggle -> {state}", flush=True)
        elif key == "o":
            safety_sensor_enabled = not safety_sensor_enabled
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
