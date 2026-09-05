import asyncio
import logging
import os
import sys
import termios
import tty

from dotenv import load_dotenv
from mavsdk import System
from mavsdk.action import ActionError
from mavsdk.offboard import OffboardError, VelocityNedYaw


load_dotenv()

PX4_CONTROL_SYSTEM_ADDRESS = os.getenv(
    "PX4_CONTROL_SYSTEM_ADDRESS",
    "udpin://0.0.0.0:14030",
)
MAVSDK_CONTROL_GRPC_PORT = int(os.getenv("MAVSDK_CONTROL_GRPC_PORT", "50052"))
MAVSDK_CONTROL_SYSID = int(os.getenv("MAVSDK_CONTROL_SYSID", "245"))
MAVSDK_CONTROL_COMPID = int(os.getenv("MAVSDK_CONTROL_COMPID", "191"))

MOVE_SPEED_M_S = float(os.getenv("CONTROL_MOVE_SPEED_M_S", "3.0"))
VERTICAL_SPEED_M_S = float(os.getenv("CONTROL_VERTICAL_SPEED_M_S", "2.0"))
YAW_DEG = float(os.getenv("CONTROL_YAW_DEG", "30.0"))


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
    print("      l land | x exit")
    print()
    print("Press one move key once to keep moving. Press k to stop/hover.")
    print()

    drone = System(
        port=MAVSDK_CONTROL_GRPC_PORT,
        sysid=MAVSDK_CONTROL_SYSID,
        compid=MAVSDK_CONTROL_COMPID,
    )
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
                    await ensure_offboard_started(drone)
                    print("[CMD] Offboard mode active. Use wasdqefv to fly, k to hover.")
                except (ActionError, OffboardError) as exc:
                    print_command_denied("takeoff/offboard", exc)
        elif key == "w":
            print("[CMD] forward")
            try:
                await set_motion(drone, MOVE_SPEED_M_S, 0.0, 0.0)
            except OffboardError as exc:
                print_command_denied("forward", exc)
        elif key == "s":
            print("[CMD] backward")
            try:
                await set_motion(drone, -MOVE_SPEED_M_S, 0.0, 0.0)
            except OffboardError as exc:
                print_command_denied("backward", exc)
        elif key == "a":
            print("[CMD] left")
            try:
                await set_motion(drone, 0.0, -MOVE_SPEED_M_S, 0.0)
            except OffboardError as exc:
                print_command_denied("left", exc)
        elif key == "d":
            print("[CMD] right")
            try:
                await set_motion(drone, 0.0, MOVE_SPEED_M_S, 0.0)
            except OffboardError as exc:
                print_command_denied("right", exc)
        elif key == "f":
            print("[CMD] up")
            try:
                await set_motion(drone, 0.0, 0.0, -VERTICAL_SPEED_M_S)
            except OffboardError as exc:
                print_command_denied("up", exc)
        elif key == "v":
            print("[CMD] down")
            try:
                await set_motion(drone, 0.0, 0.0, VERTICAL_SPEED_M_S)
            except OffboardError as exc:
                print_command_denied("down", exc)
        elif key == "q":
            print("[CMD] yaw left")
            try:
                await set_motion(drone, 0.0, 0.0, 0.0, -YAW_DEG)
            except OffboardError as exc:
                print_command_denied("yaw left", exc)
        elif key == "e":
            print("[CMD] yaw right")
            try:
                await set_motion(drone, 0.0, 0.0, 0.0, YAW_DEG)
            except OffboardError as exc:
                print_command_denied("yaw right", exc)
        elif key in ("k", "h"):
            print("[CMD] stop / hover")
            try:
                await ensure_offboard_started(drone)
                await drone.offboard.set_velocity_ned(VelocityNedYaw(0.0, 0.0, 0.0, 0.0))
            except OffboardError as exc:
                print_command_denied("stop/hover", exc)
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
        elif key == "x":
            print("[SHUTDOWN] Stopped by user")
            return


if __name__ == "__main__":
    configure_mavsdk_logging()
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[SHUTDOWN] Stopped by user")
