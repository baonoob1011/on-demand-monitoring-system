from __future__ import annotations

import asyncio
import importlib
import math
import os
import socket
import subprocess
import sys
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable

import cv2
import numpy as np
from dotenv import load_dotenv
from mavsdk import System

from sitl_battery_sim import (
    SitlBatterySimulator,
    battery_level,
    normalize_real_battery_percent,
)

GZ_PYTHON_DIST_PACKAGES = "/usr/lib/python3/dist-packages"
if GZ_PYTHON_DIST_PACKAGES not in sys.path:
    sys.path.append(GZ_PYTHON_DIST_PACKAGES)


PROJECT_ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = PROJECT_ROOT / "ondemandmonitoring" / ".env"
load_dotenv(ENV_FILE, override=True)

DEFAULT_WORLD = "forest_monitoring_compact"
DEFAULT_MODEL = "x500_mono_cam_down_0"
DEFAULT_CAMERA_TOPIC = (
    f"/world/{DEFAULT_WORLD}/model/{DEFAULT_MODEL}/link/camera_link/sensor/camera/image"
)

WORLD_NAME = os.getenv("GZ_WORLD_NAME", DEFAULT_WORLD)
MODEL_NAME = os.getenv("GZ_MODEL_NAME", DEFAULT_MODEL)
CAMERA_TOPIC = os.getenv("GAZEBO_CAMERA_TOPIC", DEFAULT_CAMERA_TOPIC)
MAVSDK_GRPC_PORT = int(os.getenv("MAVSDK_CONTROL_GRPC_PORT", "50052"))
MAVSDK_SYSID = int(os.getenv("MAVSDK_CONTROL_SYSID", "245"))
MAVSDK_COMPID = int(os.getenv("MAVSDK_CONTROL_COMPID", "191"))
WINDOW_NAME = os.getenv("DOWNWARD_CAMERA_WINDOW_TITLE", "Downward Camera")
STALE_AFTER_S = float(os.getenv("CAMERA_HUD_TELEMETRY_STALE_AFTER_S", "3.0"))
CAMERA_DEFAULT_VIEW = os.getenv("CAMERA_DEFAULT_VIEW", "DOWN").strip().upper()
CAMERA_TOGGLE_DEBOUNCE_S = float(os.getenv("CAMERA_TOGGLE_DEBOUNCE_S", "0.35"))
battery_sim = SitlBatterySimulator()


def camera_switch_button_rect(width: int, height: int) -> tuple[int, int, int, int]:
    scale = max(0.55, min(width, height) / 720.0)
    font_scale = 0.55 * scale
    margin = max(12, int(18 * scale))
    pad = max(10, int(14 * scale))
    hint = "[C] SWITCH CAMERA"
    hint_scale = font_scale * 0.95
    hint_thickness = max(1, int(2 * scale))
    (hint_w, hint_h), _baseline = cv2.getTextSize(
        hint,
        cv2.FONT_HERSHEY_SIMPLEX,
        hint_scale,
        hint_thickness,
    )
    x2 = width - margin
    x1 = max(margin, x2 - hint_w - pad * 2)
    y1 = margin
    y2 = y1 + hint_h + pad * 2
    return x1, y1, x2, y2


@dataclass
class DroneTelemetryState:
    latitude_deg: float | None = None
    longitude_deg: float | None = None
    relative_altitude_m: float | None = None
    roll_deg: float | None = None
    pitch_deg: float | None = None
    yaw_deg: float | None = None
    speed_m_s: float | None = None
    battery_percent: float | None = None
    battery_level: str | None = None
    camera_mode: str = CAMERA_DEFAULT_VIEW if CAMERA_DEFAULT_VIEW in {"DOWN", "FRONT"} else "DOWN"
    velocity_north_m_s: float | None = None
    velocity_east_m_s: float | None = None
    velocity_down_m_s: float | None = None
    satellites: int | None = None
    flight_mode: str | None = None
    armed: bool | None = None
    in_air: bool | None = None
    connected: bool = False
    last_update_s: float = 0.0
    lock: threading.Lock = field(default_factory=threading.Lock)

    def update(self, **values: Any) -> None:
        with self.lock:
            for key, value in values.items():
                setattr(self, key, value)
            self.last_update_s = time.monotonic()

    def snapshot(self) -> "DroneTelemetryState":
        with self.lock:
            return DroneTelemetryState(
                latitude_deg=self.latitude_deg,
                longitude_deg=self.longitude_deg,
                relative_altitude_m=self.relative_altitude_m,
                roll_deg=self.roll_deg,
                pitch_deg=self.pitch_deg,
                yaw_deg=self.yaw_deg,
                speed_m_s=self.speed_m_s,
                battery_percent=self.battery_percent,
                battery_level=self.battery_level,
                camera_mode=self.camera_mode,
                velocity_north_m_s=self.velocity_north_m_s,
                velocity_east_m_s=self.velocity_east_m_s,
                velocity_down_m_s=self.velocity_down_m_s,
                satellites=self.satellites,
                flight_mode=self.flight_mode,
                armed=self.armed,
                in_air=self.in_air,
                connected=self.connected,
                last_update_s=self.last_update_s,
            )


class CameraFrameStore:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.frame: np.ndarray | None = None
        self.width = 0
        self.height = 0
        self.pixel_format = "--"
        self.frame_count = 0
        self.fps = 0.0
        self._last_fps_time = time.monotonic()
        self._last_fps_count = 0

    def update(self, frame: np.ndarray, pixel_format: str) -> None:
        with self.lock:
            self.frame = frame
            self.height, self.width = frame.shape[:2]
            self.pixel_format = pixel_format
            self.frame_count += 1
            now = time.monotonic()
            elapsed = now - self._last_fps_time
            if elapsed >= 1.0:
                frames = self.frame_count - self._last_fps_count
                self.fps = frames / elapsed
                self._last_fps_count = self.frame_count
                self._last_fps_time = now

    def snapshot(self) -> tuple[np.ndarray | None, int, int, str, float]:
        with self.lock:
            frame = None if self.frame is None else self.frame.copy()
            return frame, self.width, self.height, self.pixel_format, self.fps


class GazeboCameraOrientationController:
    def __init__(self, world_name: str, model_name: str) -> None:
        self.world_name = world_name
        self.model_name = model_name
        default_topic = f"/model/{self.model_name}/command/camera_pitch"
        self.command_topic = os.getenv("GAZEBO_CAMERA_PITCH_TOPIC", default_topic)
        self.front_position_rad = float(os.getenv("CAMERA_FRONT_JOINT_POSITION_RAD", "-1.57079632679"))
        self.down_position_rad = float(os.getenv("CAMERA_DOWN_JOINT_POSITION_RAD", "0.0"))
        self.current_mode = CAMERA_DEFAULT_VIEW if CAMERA_DEFAULT_VIEW in {"DOWN", "FRONT"} else "DOWN"
        self._last_toggle_s = 0.0

    def toggle(self) -> str | None:
        now = time.monotonic()
        if now - self._last_toggle_s < CAMERA_TOGGLE_DEBOUNCE_S:
            return None
        self._last_toggle_s = now

        next_mode = "FRONT" if self.current_mode == "DOWN" else "DOWN"
        if self.request_mode(next_mode):
            self.current_mode = next_mode
            print(f"[C] Camera view -> {next_mode}", flush=True)
            return next_mode
        return None

    def request_mode(self, mode: str) -> bool:
        if not self._command_topic_available():
            print(f"[CAMERA] Failed to set {mode}: command topic not ready: {self.command_topic}", flush=True)
            return False

        joint_position = self.front_position_rad if mode == "FRONT" else self.down_position_rad
        command = [
            "gz",
            "topic",
            "-t",
            self.command_topic,
            "-m",
            "gz.msgs.Double",
            "-p",
            f"data: {joint_position:.12f}",
        ]

        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=1.5, check=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            print(f"[CAMERA] Failed to set {mode}: {exc}", flush=True)
            return False

        if result.returncode == 0:
            return True

        message = (result.stderr or result.stdout or "Gazebo service returned no success").strip()
        print(f"[CAMERA] Failed to set {mode}: {message}", flush=True)
        return False

    def _command_topic_available(self) -> bool:
        try:
            result = subprocess.run(
                ["gz", "topic", "-l"],
                capture_output=True,
                text=True,
                timeout=1.5,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            return False

        if result.returncode != 0:
            return False

        return any(line.strip() == self.command_topic for line in result.stdout.splitlines())


def clean_enum_name(value: Any) -> str | None:
    if value is None:
        return None
    name = getattr(value, "name", None)
    if name:
        return str(name)
    return str(value).rsplit(".", maxsplit=1)[-1]


def number_or_none(value: Any) -> float | None:
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    if math.isnan(result):
        return None
    return result


def format_float(value: float | None, suffix: str = "", precision: int = 1) -> str:
    if value is None:
        return "--"
    return f"{value:.{precision}f}{suffix}"


def format_bool(value: bool | None) -> str:
    if value is None:
        return "--"
    return "YES" if value else "NO"


def format_gps(state: DroneTelemetryState) -> str:
    if state.latitude_deg is None or state.longitude_deg is None:
        return "--"
    return f"{state.latitude_deg:.6f}, {state.longitude_deg:.6f}"


def normalize_yaw(value: float | None) -> float | None:
    if value is None:
        return None
    return value % 360.0


def load_gazebo_type(module_names: tuple[str, ...], class_name: str) -> tuple[type | None, Exception | None]:
    last_error = None
    for module_name in module_names:
        try:
            module = importlib.import_module(module_name)
            return getattr(module, class_name), None
        except (ImportError, AttributeError) as exc:
            last_error = exc
    return None, last_error


Node, NODE_IMPORT_ERROR = load_gazebo_type(("gz.transport13",), "Node")
ImageMsg, IMAGE_IMPORT_ERROR = load_gazebo_type(("gz.msgs10.image_pb2",), "Image")


class GazeboCameraSubscriber:
    def __init__(self, topic: str, store: CameraFrameStore) -> None:
        self.topic = topic
        self.store = store
        self.node = None

    def start(self) -> bool:
        if Node is None or ImageMsg is None:
            print(
                f"[CAMERA] Gazebo Python bindings unavailable: "
                f"{NODE_IMPORT_ERROR or IMAGE_IMPORT_ERROR}",
                flush=True,
            )
            return False

        self.node = Node()
        self.node.subscribe(ImageMsg, self.topic, self._on_image)
        print(f"[CAMERA] Listening to drone sensor: {self.topic}", flush=True)
        return True

    def _on_image(self, msg: Any, *_args: Any) -> None:
        width = int(getattr(msg, "width", 0) or 0)
        height = int(getattr(msg, "height", 0) or 0)
        if width <= 0 or height <= 0:
            return

        data = bytes(getattr(msg, "data", b""))
        if not data:
            return

        pixel_format_id = int(getattr(msg, "pixel_format_type", 0) or 0)
        pixel_format = str(pixel_format_id)
        expected_rgb = width * height * 3
        expected_rgba = width * height * 4

        try:
            if len(data) >= expected_rgba:
                rgba = np.frombuffer(data[:expected_rgba], dtype=np.uint8).reshape((height, width, 4))
                frame = cv2.cvtColor(rgba, cv2.COLOR_RGBA2BGR)
                pixel_format = "RGBA8"
            elif len(data) >= expected_rgb:
                rgb = np.frombuffer(data[:expected_rgb], dtype=np.uint8).reshape((height, width, 3))
                frame = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
                pixel_format = "RGB8"
            else:
                return
        except ValueError:
            return

        self.store.update(frame, pixel_format)


async def wait_for_grpc_port(port: int, timeout_s: float) -> bool:
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.4):
                return True
        except OSError:
            await asyncio.sleep(0.5)
    return False


async def run_stream(name: str, stream: Callable[[], Any], handler: Callable[[Any], None]) -> None:
    async for item in stream():
        handler(item)


async def telemetry_supervisor(state: DroneTelemetryState) -> None:
    while True:
        try:
            ready = await wait_for_grpc_port(MAVSDK_GRPC_PORT, 5.0)
            if not ready:
                state.update(connected=False)
                await asyncio.sleep(1.0)
                continue

            drone = System(
                mavsdk_server_address="localhost",
                port=MAVSDK_GRPC_PORT,
                sysid=MAVSDK_SYSID,
                compid=MAVSDK_COMPID,
            )
            await drone.connect()

            async for connection_state in drone.core.connection_state():
                state.update(connected=connection_state.is_connected)
                if connection_state.is_connected:
                    break

            await asyncio.gather(
                run_stream(
                    "position",
                    drone.telemetry.position,
                    lambda position: state.update(
                        latitude_deg=number_or_none(position.latitude_deg),
                        longitude_deg=number_or_none(position.longitude_deg),
                        relative_altitude_m=number_or_none(position.relative_altitude_m),
                    ),
                ),
                run_stream(
                    "attitude",
                    drone.telemetry.attitude_euler,
                    lambda attitude: state.update(
                        roll_deg=number_or_none(attitude.roll_deg),
                        pitch_deg=number_or_none(attitude.pitch_deg),
                        yaw_deg=normalize_yaw(number_or_none(attitude.yaw_deg)),
                    ),
                ),
                run_stream(
                    "velocity",
                    drone.telemetry.velocity_ned,
                    lambda velocity: state.update(
                        speed_m_s=math.sqrt(
                            float(velocity.north_m_s) ** 2 + float(velocity.east_m_s) ** 2
                        ),
                        velocity_north_m_s=number_or_none(velocity.north_m_s),
                        velocity_east_m_s=number_or_none(velocity.east_m_s),
                        velocity_down_m_s=number_or_none(velocity.down_m_s),
                    ),
                ),
                run_stream(
                    "battery",
                    drone.telemetry.battery,
                    lambda battery: update_real_battery(state, battery),
                ),
                run_stream(
                    "gps_info",
                    drone.telemetry.gps_info,
                    lambda gps_info: state.update(
                        satellites=getattr(gps_info, "num_satellites", None),
                    ),
                ),
                run_stream(
                    "flight_mode",
                    drone.telemetry.flight_mode,
                    lambda flight_mode: state.update(flight_mode=clean_enum_name(flight_mode)),
                ),
                run_stream(
                    "armed",
                    drone.telemetry.armed,
                    lambda armed: state.update(armed=bool(armed)),
                ),
                run_stream(
                    "in_air",
                    drone.telemetry.in_air,
                    lambda in_air: state.update(in_air=bool(in_air)),
                ),
            )
        except asyncio.CancelledError:
            raise
        except Exception:
            state.update(connected=False)
            await asyncio.sleep(1.5)


def update_real_battery(state: DroneTelemetryState, battery: Any) -> None:
    if battery_sim.enabled:
        return

    percent = normalize_real_battery_percent(number_or_none(battery.remaining_percent))
    state.update(
        battery_percent=percent,
        battery_level=battery_level(percent),
    )


async def sitl_battery_loop(state: DroneTelemetryState) -> None:
    while True:
        snapshot = battery_sim.snapshot()
        if snapshot is not None:
            state.update(
                battery_percent=snapshot.percent,
                battery_level=snapshot.level,
            )
        await asyncio.sleep(0.5)


def draw_hud(frame: np.ndarray, state: DroneTelemetryState, topic: str, fps: float, pixel_format: str) -> np.ndarray:
    output = frame.copy()
    height, width = output.shape[:2]
    scale = max(0.55, min(width, height) / 720.0)
    font_scale = 0.55 * scale
    line_h = max(17, int(23 * scale))
    margin = max(12, int(18 * scale))
    pad = max(10, int(14 * scale))
    hud_w = min(width - margin * 2, max(int(330 * scale), 290))

    stale = (time.monotonic() - state.last_update_s) > STALE_AFTER_S if state.last_update_s else True
    telemetry_lost = stale or not state.connected

    if telemetry_lost:
        rows = [
            ("DRONE TELEMETRY", ""),
            ("TELEMETRY", "LOST"),
            ("GPS", "--"),
            ("ALT", "--"),
            ("ROLL", "--"),
            ("PITCH", "--"),
            ("YAW", "--"),
            ("SPEED", "--"),
            ("BATTERY", "--"),
            ("SAT", "--"),
            ("MODE", "--"),
            ("ARMED", "--"),
            ("IN AIR", "--"),
            ("CAM", state.camera_mode),
        ]
    else:
        rows = [
            ("DRONE TELEMETRY", ""),
            ("GPS", format_gps(state)),
            ("ALT", format_float(state.relative_altitude_m, " m", 1)),
            ("", ""),
            ("ROLL", format_float(state.roll_deg, " deg", 1)),
            ("PITCH", format_float(state.pitch_deg, " deg", 1)),
            ("YAW", format_float(state.yaw_deg, " deg", 1)),
            ("", ""),
            ("SPEED", format_float(state.speed_m_s, " m/s", 1)),
            (
                "BATTERY",
                "--"
                if state.battery_percent is None
                else (
                    f"{state.battery_percent:.0f}%"
                    if state.battery_level in {None, "NORMAL"}
                    else f"{state.battery_percent:.0f}% [{state.battery_level}]"
                ),
            ),
            ("SAT", "--" if state.satellites is None else str(state.satellites)),
            ("MODE", state.flight_mode or "--"),
            ("ARMED", format_bool(state.armed)),
            ("IN AIR", format_bool(state.in_air)),
            ("CAM", state.camera_mode),
        ]

    hud_h = pad * 2 + line_h * len(rows)
    x1 = margin
    y1 = max(margin, height - hud_h - margin)
    x2 = x1 + hud_w
    y2 = y1 + hud_h

    overlay = output.copy()
    cv2.rectangle(overlay, (x1, y1), (x2, y2), (20, 20, 20), -1)
    output = cv2.addWeighted(overlay, 0.62, output, 0.38, 0)

    text_x = x1 + pad
    value_x = x1 + int(122 * scale)
    y = y1 + pad + line_h
    for label, value in rows:
        if label == "DRONE TELEMETRY":
            cv2.putText(
                output,
                label,
                (text_x, y),
                cv2.FONT_HERSHEY_SIMPLEX,
                font_scale * 1.05,
                (255, 255, 255),
                max(1, int(2 * scale)),
                cv2.LINE_AA,
            )
            cv2.line(output, (text_x, y + 7), (x2 - pad, y + 7), (40, 180, 230), 1)
        elif label:
            cv2.putText(
                output,
                label,
                (text_x, y),
                cv2.FONT_HERSHEY_SIMPLEX,
                font_scale,
                (245, 245, 245),
                max(1, int(2 * scale)),
                cv2.LINE_AA,
            )
            cv2.putText(
                output,
                value,
                (value_x, y),
                cv2.FONT_HERSHEY_SIMPLEX,
                font_scale,
                (255, 255, 255),
                max(1, int(2 * scale)),
                cv2.LINE_AA,
            )
        y += line_h

    info_lines = [
        f"Resolution: {width} x {height}  |  {fps:.0f} Hz",
        f"Topic: {topic}  |  {pixel_format}",
    ]
    info_w = min(width - margin * 2, max(int(410 * scale), 300))
    info_h = pad * 2 + line_h * len(info_lines)
    ix2 = width - margin
    ix1 = max(margin, ix2 - info_w)
    iy2 = height - margin
    iy1 = max(margin, iy2 - info_h)
    if ix1 < x2 + margin and iy1 < y2 + margin:
        iy2 = y1 - margin
        iy1 = max(margin, iy2 - info_h)

    overlay = output.copy()
    cv2.rectangle(overlay, (ix1, iy1), (ix2, iy2), (20, 20, 20), -1)
    output = cv2.addWeighted(overlay, 0.50, output, 0.50, 0)

    iy = iy1 + pad + line_h
    for line in info_lines:
        cv2.putText(
            output,
            line,
            (ix1 + pad, iy),
            cv2.FONT_HERSHEY_SIMPLEX,
            font_scale * 0.9,
            (255, 255, 255),
            max(1, int(2 * scale)),
            cv2.LINE_AA,
        )
        iy += line_h

    hint = "[C] SWITCH CAMERA"
    hint_scale = font_scale * 0.95
    hint_thickness = max(1, int(2 * scale))
    hx1, hy1, hx2, hy2 = camera_switch_button_rect(width, height)
    overlay = output.copy()
    cv2.rectangle(overlay, (hx1, hy1), (hx2, hy2), (28, 56, 96), -1)
    output = cv2.addWeighted(overlay, 0.72, output, 0.28, 0)
    cv2.rectangle(output, (hx1, hy1), (hx2, hy2), (235, 235, 235), 1)
    cv2.putText(
        output,
        hint,
        (hx1 + pad, hy2 - pad),
        cv2.FONT_HERSHEY_SIMPLEX,
        hint_scale,
        (255, 255, 255),
        hint_thickness,
        cv2.LINE_AA,
    )

    return output


def draw_waiting_frame(topic: str) -> np.ndarray:
    frame = np.full((480, 640, 3), (32, 32, 32), dtype=np.uint8)
    cv2.putText(frame, "Waiting for Gazebo camera...", (36, 210), cv2.FONT_HERSHEY_SIMPLEX, 0.75, (255, 255, 255), 2)
    cv2.putText(frame, topic, (36, 250), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (210, 210, 210), 1)
    return frame


async def run_telemetry_tasks(state: DroneTelemetryState) -> None:
    await asyncio.gather(
        telemetry_supervisor(state),
        sitl_battery_loop(state),
    )


def run_telemetry_thread(state: DroneTelemetryState) -> None:
    asyncio.run(run_telemetry_tasks(state))


def main() -> int:
    telemetry_state = DroneTelemetryState()
    frame_store = CameraFrameStore()
    subscriber = GazeboCameraSubscriber(CAMERA_TOPIC, frame_store)
    camera_controller = GazeboCameraOrientationController(WORLD_NAME, MODEL_NAME)

    if not subscriber.start():
        return 1

    telemetry_thread = threading.Thread(
        target=run_telemetry_thread,
        args=(telemetry_state,),
        name="camera-hud-telemetry",
        daemon=True,
    )
    telemetry_thread.start()

    cv2.namedWindow(WINDOW_NAME, cv2.WINDOW_NORMAL)
    cv2.resizeWindow(WINDOW_NAME, 960, 720)

    print(f"[HUD] Reading telemetry from shared MAVSDK gRPC localhost:{MAVSDK_GRPC_PORT}", flush=True)
    print("[HUD] Read-only display mode; no flight commands are sent.", flush=True)
    print("[HUD] Press C or click [C] SWITCH CAMERA to toggle FRONT/DOWN camera.", flush=True)

    def switch_camera() -> None:
        mode = camera_controller.toggle()
        if mode is not None:
            telemetry_state.update(camera_mode=mode)

    def handle_mouse(event: int, x: int, y: int, _flags: int, _param: object) -> None:
        if event != cv2.EVENT_LBUTTONDOWN:
            return
        frame, width, height, _pixel_format, _fps = frame_store.snapshot()
        if frame is None:
            width, height = 640, 480
        x1, y1, x2, y2 = camera_switch_button_rect(width, height)
        if x1 <= x <= x2 and y1 <= y <= y2:
            switch_camera()

    cv2.setMouseCallback(WINDOW_NAME, handle_mouse)

    try:
        while True:
            frame, _width, _height, pixel_format, fps = frame_store.snapshot()
            if frame is None:
                frame = draw_waiting_frame(CAMERA_TOPIC)
                pixel_format = "--"

            state_snapshot = telemetry_state.snapshot()
            output = draw_hud(frame, state_snapshot, CAMERA_TOPIC, fps, pixel_format)
            cv2.imshow(WINDOW_NAME, output)

            key = cv2.waitKeyEx(1)
            key_char = chr(key & 0xFF).lower() if 0 <= key <= 0x10FFFF else ""
            if key == 27 or key_char in {"q", "x"}:
                break
            if key_char == "c":
                switch_camera()
    finally:
        cv2.destroyAllWindows()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
