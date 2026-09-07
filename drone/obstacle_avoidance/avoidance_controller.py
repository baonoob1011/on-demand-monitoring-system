import math

from mavsdk import System
from mavsdk.offboard import VelocityNedYaw


class AvoidanceController:
    def __init__(self, drone: System):
        self.drone = drone
        self.current_yaw = 0.0

    def set_drone(self, drone: System):
        self.drone = drone

    def set_yaw(self, yaw_deg: float):
        self.current_yaw = yaw_deg % 360.0

    def body_velocity(
            self,
            forward: float,
            right: float,
    ) -> tuple[float, float]:
        """
        Convert body-frame velocity to NED.

        forward > 0 = forward
        forward < 0 = backward

        right > 0 = right
        right < 0 = left
        """

        yaw_rad = math.radians(self.current_yaw)

        north = (
                forward * math.cos(yaw_rad)
                - right * math.sin(yaw_rad)
        )

        east = (
                forward * math.sin(yaw_rad)
                + right * math.cos(yaw_rad)
        )

        return north, east

    async def hover(self, log: bool = True):
        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                0.0,
                0.0,
                0.0,
                self.current_yaw,
            )
        )

        if log:
            print("[AVOID] HOVER", flush=True)

    async def move_forward(self, speed: float = 2.0):
        north, east = self.body_velocity(
            forward=speed,
            right=0.0,
        )

        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                north,
                east,
                0.0,
                self.current_yaw,
            )
        )

        print("[AVOID] MOVE FORWARD")

    async def move_back(self, speed: float = 2.0):
        north, east = self.body_velocity(
            forward=-speed,
            right=0.0,
        )

        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                north,
                east,
                0.0,
                self.current_yaw,
            )
        )

        print("[AVOID] MOVE BACK")

    async def move_left(self, speed: float = 2.0):
        north, east = self.body_velocity(
            forward=0.0,
            right=-speed,
        )

        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                north,
                east,
                0.0,
                self.current_yaw,
            )
        )

        print("[AVOID] MOVE LEFT")

    async def move_right(self, speed: float = 2.0):
        north, east = self.body_velocity(
            forward=0.0,
            right=speed,
        )

        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                north,
                east,
                0.0,
                self.current_yaw,
            )
        )

        print("[AVOID] MOVE RIGHT")

    async def move_up(self, speed: float = 1.5):
        """
        NED:
        Down < 0 => đi lên.
        """

        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                0.0,
                0.0,
                -speed,
                self.current_yaw,
            )
        )

        print("[AVOID] MOVE UP")

    async def move_down(self, speed: float = 1.5):
        """
        NED:
        Down > 0 => đi xuống.
        """

        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                0.0,
                0.0,
                speed,
                self.current_yaw,
            )
        )

        print("[AVOID] MOVE DOWN")

    async def yaw_left(self, yaw_step_deg: float = 30.0):
        self.set_yaw(self.current_yaw - yaw_step_deg)

        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                0.0,
                0.0,
                0.0,
                self.current_yaw,
            )
        )

        print(f"[AVOID] YAW LEFT -> {self.current_yaw:.0f} deg", flush=True)

    async def yaw_right(self, yaw_step_deg: float = 30.0):
        self.set_yaw(self.current_yaw + yaw_step_deg)

        await self.drone.offboard.set_velocity_ned(
            VelocityNedYaw(
                0.0,
                0.0,
                0.0,
                self.current_yaw,
            )
        )

        print(f"[AVOID] YAW RIGHT -> {self.current_yaw:.0f} deg", flush=True)
