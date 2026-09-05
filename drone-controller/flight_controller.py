import asyncio
from datetime import datetime, timezone
from io import BytesIO
import logging
import os
import sys
import termios
import threading
import tty

import grpc
import httpx
from dotenv import load_dotenv
from mavsdk import System
from mavsdk.action import ActionError
from mavsdk.offboard import OffboardError, VelocityNedYaw
from PIL import Image as PilImage

if "/usr/lib/python3/dist-packages" not in sys.path:
    sys.path.append("/usr/lib/python3/dist-packages")

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

MOVE_SPEED_M_S = float(os.getenv("CONTROL_MOVE_SPEED_M_S", "5.0"))
VERTICAL_SPEED_M_S = float(os.getenv("CONTROL_VERTICAL_SPEED_M_S", "3.0"))
YAW_DEG = float(os.getenv("CONTROL_YAW_DEG", "45.0"))
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
DEVICE_CODE = os.getenv("DEVICE_CODE", "DRONE-01")
DRONE_ID = os.getenv("DRONE_ID", DEVICE_CODE)
MISSION_ID = os.getenv("MISSION_ID", "MISSION_001")
CAMERA_TOPIC = os.getenv(
    "GAZEBO_CAMERA_TOPIC",
    "/world/forest_monitoring/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image",
)


class MavsdkAckNoiseFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        message = record.getMessage()
        return "Received ack for not-existing command: 512" not in message


def configure_mavsdk_logging() -> None:
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
            print("[CAMERA] Image uploaded successfully")
            return

        print(f"[CAMERA] Upload failed - HTTP {response.status_code}")
        print(response.text[:500])


async def connect_px4(drone: System) -> None:
    print("[PX4] Waiting for control connection...")
    await drone.connect(system_address=PX4_CONTROL_SYSTEM_ADDRESS)

    async for state in drone.core.connection_state():
        if state.is_connected:
            print("[PX4] Control connected")
            break

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
                print(f"[PX4] Health: {status}", end="\r")
                if health.is_local_position_ok and health.is_global_position_ok:
                    print("\n[PX4] Local position OK - Ready to fly!")
                    return
    except asyncio.TimeoutError:
        print("\n[WARN] Local position timeout. Drone may still be initialising.")
        print("[WARN] If PX4 shows 'Ready for takeoff!' you can still try pressing t.")


def print_command_denied(command: str, exc: Exception) -> None:
    print(f"[WARN] {command} failed: {exc}")
    print("[HINT] Check PX4 terminal shows 'Ready for takeoff!' then press t again.")


def print_mavsdk_unavailable(command: str, exc: Exception) -> None:
    print(f"[WARN] {command} failed: MAVSDK connection unavailable ({exc})")
    print("[HINT] PX4/Gazebo may still be running. Restart only the Flight Control tab, or rerun start-drone-stack.cmd.")


async def ensure_offboard_started(drone: System) -> None:
    await drone.offboard.set_velocity_ned(VelocityNedYaw(0.0, 0.0, 0.0, 0.0))
    try:
        await drone.offboard.start()
    except OffboardError as exc:
        if exc._result.result_str != "BUSY":
            raise


async def safe_arm(drone: System) -> bool:
    """Arm with retry and clear error reporting."""
    for attempt in range(3):
        try:
            print(f"[CMD] Arming... (attempt {attempt+1}/3)")
            await drone.action.arm()
            print("[CMD] Armed successfully!")
            return True
        except ActionError as e:
            print(f"[WARN] Arm failed: {e}")
            if attempt < 2:
                print("[HINT] Retrying in 2s...")
                await asyncio.sleep(2)
        except grpc.aio.AioRpcError as exc:
            print_mavsdk_unavailable("arm", exc)
            if attempt < 2:
                print("[HINT] Retrying arm in 2s...")
                await asyncio.sleep(2)
    print("[ERR] Could not arm after 3 attempts.")
    print("[ERR] Ensure PX4 terminal shows: 'Ready for takeoff!'")
    print("[ERR] Check: sensors OK, no safety switch active.")
    return False


async def set_motion(drone: System, north: float, east: float, down: float, yaw_deg: float = 0.0) -> None:
    await ensure_offboard_started(drone)
    await drone.offboard.set_velocity_ned(VelocityNedYaw(north, east, down, yaw_deg))


async def main() -> None:
    print("========================================")
    print(" On-Demand Monitoring Flight Controller")
    print("========================================")
    print(f"PX4 control: {PX4_CONTROL_SYSTEM_ADDRESS}")
    print(f"MAVSDK gRPC: localhost:{MAVSDK_CONTROL_GRPC_PORT}")
    print()
    print("Keys: t takeoff | w forward | s back | a left | d right")
    print("      f up | v down | q yaw left | e yaw right | k stop")
    print("      p photo | l land | x exit")
    print()
    print("Press one move key once to keep moving. Press k to stop/hover.")
    print()

    drone = System(
        port=MAVSDK_CONTROL_GRPC_PORT,
        sysid=MAVSDK_CONTROL_SYSID,
        compid=MAVSDK_CONTROL_COMPID,
    )
    camera = CameraGateway()
    camera.start()
    await connect_px4(drone)

    while True:
        key = await asyncio.to_thread(read_key)

        if key == "t":
            print("[CMD] arm + takeoff")
            armed = await safe_arm(drone)
            if armed:
                try:
                    await drone.action.takeoff()
                    print("[CMD] Takeoff command sent - climbing...")
                    await asyncio.sleep(5)
                    print("[CMD] Takeoff complete. Use wasdqefv to start offboard flight, k to hover.")
                except (ActionError, OffboardError) as exc:
                    print_command_denied("takeoff/offboard", exc)
                except grpc.aio.AioRpcError as exc:
                    print_mavsdk_unavailable("takeoff", exc)
        elif key == "w":
            print("[CMD] forward")
            try:
                await set_motion(drone, MOVE_SPEED_M_S, 0.0, 0.0)
            except OffboardError as exc:
                print_command_denied("forward", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("forward", exc)
        elif key == "s":
            print("[CMD] backward")
            try:
                await set_motion(drone, -MOVE_SPEED_M_S, 0.0, 0.0)
            except OffboardError as exc:
                print_command_denied("backward", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("backward", exc)
        elif key == "a":
            print("[CMD] left")
            try:
                await set_motion(drone, 0.0, -MOVE_SPEED_M_S, 0.0)
            except OffboardError as exc:
                print_command_denied("left", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("left", exc)
        elif key == "d":
            print("[CMD] right")
            try:
                await set_motion(drone, 0.0, MOVE_SPEED_M_S, 0.0)
            except OffboardError as exc:
                print_command_denied("right", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("right", exc)
        elif key == "f":
            print("[CMD] up")
            try:
                await set_motion(drone, 0.0, 0.0, -VERTICAL_SPEED_M_S)
            except OffboardError as exc:
                print_command_denied("up", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("up", exc)
        elif key == "v":
            print("[CMD] down")
            try:
                await set_motion(drone, 0.0, 0.0, VERTICAL_SPEED_M_S)
            except OffboardError as exc:
                print_command_denied("down", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("down", exc)
        elif key == "q":
            print("[CMD] yaw left")
            try:
                await set_motion(drone, 0.0, 0.0, 0.0, -YAW_DEG)
            except OffboardError as exc:
                print_command_denied("yaw left", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("yaw left", exc)
        elif key == "e":
            print("[CMD] yaw right")
            try:
                await set_motion(drone, 0.0, 0.0, 0.0, YAW_DEG)
            except OffboardError as exc:
                print_command_denied("yaw right", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("yaw right", exc)
        elif key in ("k", "h"):
            print("[CMD] stop / hover")
            try:
                await ensure_offboard_started(drone)
                await drone.offboard.set_velocity_ned(VelocityNedYaw(0.0, 0.0, 0.0, 0.0))
            except OffboardError as exc:
                print_command_denied("stop/hover", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("stop/hover", exc)
        elif key == "p":
            task = asyncio.create_task(camera.capture_and_upload())
            task.add_done_callback(
                lambda done: print(f"[CAMERA] Background error: {done.exception()}")
                if done.exception()
                else None
            )
        elif key == "l":
            print("[CMD] land")
            try:
                await drone.offboard.stop()
            except OffboardError:
                pass
            try:
                await drone.action.land()
            except ActionError as exc:
                print_command_denied("land", exc)
            except grpc.aio.AioRpcError as exc:
                print_mavsdk_unavailable("land", exc)
        elif key == "x":
            print("[SHUTDOWN] Stopped by user")
            return


if __name__ == "__main__":
    configure_mavsdk_logging()
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[SHUTDOWN] Stopped by user")
