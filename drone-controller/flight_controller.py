import asyncio
import os
import sys
import termios
import tty

from dotenv import load_dotenv
from mavsdk import System
from mavsdk.offboard import OffboardError, VelocityNedYaw


load_dotenv()

PX4_CONTROL_SYSTEM_ADDRESS = os.getenv(
    "PX4_CONTROL_SYSTEM_ADDRESS",
    "udpin://0.0.0.0:14030",
)

MOVE_SPEED_M_S = float(os.getenv("CONTROL_MOVE_SPEED_M_S", "1.0"))
VERTICAL_SPEED_M_S = float(os.getenv("CONTROL_VERTICAL_SPEED_M_S", "0.7"))
YAW_DEG = float(os.getenv("CONTROL_YAW_DEG", "30.0"))


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
            return


async def ensure_offboard_started(drone: System) -> None:
    await drone.offboard.set_velocity_ned(VelocityNedYaw(0.0, 0.0, 0.0, 0.0))
    try:
        await drone.offboard.start()
    except OffboardError as exc:
        if exc._result.result_str != "BUSY":
            raise


async def set_motion(drone: System, north: float, east: float, down: float, yaw_deg: float = 0.0) -> None:
    await ensure_offboard_started(drone)
    await drone.offboard.set_velocity_ned(VelocityNedYaw(north, east, down, yaw_deg))


async def main() -> None:
    print("========================================")
    print(" On-Demand Monitoring Flight Controller")
    print("========================================")
    print(f"PX4 control: {PX4_CONTROL_SYSTEM_ADDRESS}")
    print()
    print("Keys: t takeoff | w forward | s back | a left | d right")
    print("      f up | v down | q yaw left | e yaw right | k stop")
    print("      l land | x exit")
    print()
    print("Press one move key once to keep moving. Press k to stop/hover.")
    print()

    drone = System()
    await connect_px4(drone)

    while True:
        key = await asyncio.to_thread(read_key)

        if key == "t":
            print("[CMD] arm + takeoff")
            await drone.action.arm()
            await drone.action.takeoff()
            await asyncio.sleep(3)
            await ensure_offboard_started(drone)
        elif key == "w":
            print("[CMD] forward until stop")
            await set_motion(drone, MOVE_SPEED_M_S, 0.0, 0.0)
        elif key == "s":
            print("[CMD] backward until stop")
            await set_motion(drone, -MOVE_SPEED_M_S, 0.0, 0.0)
        elif key == "a":
            print("[CMD] left until stop")
            await set_motion(drone, 0.0, -MOVE_SPEED_M_S, 0.0)
        elif key == "d":
            print("[CMD] right until stop")
            await set_motion(drone, 0.0, MOVE_SPEED_M_S, 0.0)
        elif key == "f":
            print("[CMD] up until stop")
            await set_motion(drone, 0.0, 0.0, -VERTICAL_SPEED_M_S)
        elif key == "v":
            print("[CMD] down until stop")
            await set_motion(drone, 0.0, 0.0, VERTICAL_SPEED_M_S)
        elif key == "q":
            print("[CMD] yaw left until stop")
            await set_motion(drone, 0.0, 0.0, 0.0, -YAW_DEG)
        elif key == "e":
            print("[CMD] yaw right until stop")
            await set_motion(drone, 0.0, 0.0, 0.0, YAW_DEG)
        elif key in ("k", "h"):
            print("[CMD] stop / hover")
            await ensure_offboard_started(drone)
            await drone.offboard.set_velocity_ned(VelocityNedYaw(0.0, 0.0, 0.0, 0.0))
        elif key == "l":
            print("[CMD] land")
            try:
                await drone.offboard.stop()
            except OffboardError:
                pass
            await drone.action.land()
        elif key == "x":
            print("[SHUTDOWN] Stopped by user")
            return


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[SHUTDOWN] Stopped by user")
