from __future__ import annotations

from dataclasses import dataclass, replace
import json
import os
import threading
import time
from typing import Any
from urllib.error import URLError
from urllib.request import Request, urlopen

import cv2
import numpy as np

try:
    cv2.setLogLevel(2)
except AttributeError:
    pass


CONTROL_BASE_URL = os.getenv("FLIGHT_CONTROL_API_URL", "http://localhost:8090").rstrip("/")
THERMAL_TOPIC = os.getenv(
    "GAZEBO_THERMAL_CAMERA_TOPIC",
    "/thermal_camera",
)
THERMAL_STREAM_URL = os.getenv("THERMAL_STREAM_URL", f"{CONTROL_BASE_URL}/thermal-stream.mjpg")
THERMAL_STATUS_URL = os.getenv("THERMAL_STATUS_URL", f"{CONTROL_BASE_URL}/api/control/status")
THERMAL_HOTSPOT_THRESHOLD_C = float(os.getenv("THERMAL_HOTSPOT_THRESHOLD_C", "60"))
THERMAL_DISPLAY_MIN_C = float(os.getenv("THERMAL_DISPLAY_MIN_C", "20"))
THERMAL_DISPLAY_MAX_C = float(os.getenv("THERMAL_DISPLAY_MAX_C", "300"))
THERMAL_STALE_FRAME_MS = int(os.getenv("THERMAL_STALE_FRAME_MS", "1500"))


@dataclass(frozen=True)
class ThermalStatus:
    online: bool = False
    enabled: bool = False
    sensor_online: bool = False
    frame_age_ms: int | None = None
    min_temp_c: float | None = None
    max_temp_c: float | None = None
    avg_temp_c: float | None = None
    hotspot_detected: bool | None = None
    hotspot_temp_c: float | None = None
    width: int | None = None
    height: int | None = None
    mode: str = "SYNTHETIC_THERMAL"
    pixel_format: str | None = None
    palette: str = "IRON"
    isotherm_enabled: bool = False
    debug_overlay_enabled: bool = False
    display_range_mode: str = "FIXED"
    error: str | None = None


def _put(img, text: str, origin: tuple[int, int], scale: float = 0.55, color=(235, 235, 235), thickness: int = 1) -> None:
    cv2.putText(img, text, origin, cv2.FONT_HERSHEY_SIMPLEX, scale, color, thickness, cv2.LINE_AA)


def _number(value: Any) -> float | None:
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return None
    return numeric if np.isfinite(numeric) else None


def parse_status(payload: dict[str, Any] | None) -> ThermalStatus:
    if not isinstance(payload, dict):
        return ThermalStatus(error="controller unavailable")

    min_temp = _number(payload.get("minTemperatureC"))
    max_temp = _number(payload.get("maxTemperatureC"))
    avg_temp = _number(payload.get("averageTemperatureC"))
    hotspot_temp = _number(payload.get("hotspotTemperatureC"))
    frame_age = payload.get("thermalFrameAgeMs")
    try:
        frame_age_ms = int(frame_age) if frame_age is not None else None
    except (TypeError, ValueError):
        frame_age_ms = None

    return ThermalStatus(
        online=bool(payload.get("online", True)),
        enabled=bool(payload.get("thermalEnabled", False)),
        sensor_online=bool(payload.get("thermalSensorOnline", False)),
        frame_age_ms=frame_age_ms,
        min_temp_c=min_temp,
        max_temp_c=max_temp,
        avg_temp_c=avg_temp,
        hotspot_detected=bool(payload.get("hotspotDetected")) if payload.get("hotspotDetected") is not None else None,
        hotspot_temp_c=hotspot_temp,
        width=int(payload.get("thermalFrameWidth")) if payload.get("thermalFrameWidth") else None,
        height=int(payload.get("thermalFrameHeight")) if payload.get("thermalFrameHeight") else None,
        mode=str(payload.get("thermalMode") or "SYNTHETIC_THERMAL"),
        pixel_format=str(payload.get("thermalPixelFormat")) if payload.get("thermalPixelFormat") else None,
        palette=str(payload.get("thermalPalette") or "IRON"),
        isotherm_enabled=bool(payload.get("thermalIsothermEnabled", False)),
        debug_overlay_enabled=bool(payload.get("thermalDebugOverlayEnabled", False)),
        display_range_mode=str(payload.get("thermalDisplayRangeMode") or "FIXED"),
        error=payload.get("thermalSourceError"),
    )


def send_thermal_command(command: str) -> bool:
    request = Request(
        f"{CONTROL_BASE_URL}/api/control/command",
        data=json.dumps({"command": command}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=0.8) as response:
            return 200 <= response.status < 300
    except (OSError, URLError):
        return False


class ThermalStatusPoller:
    def __init__(self, url: str = THERMAL_STATUS_URL, interval_s: float = 0.75) -> None:
        self.url = url
        self.interval_s = interval_s
        self._lock = threading.Lock()
        self._status = ThermalStatus()
        self._running = False
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._running = True
        self._thread = threading.Thread(target=self._run, name="thermal-status-poller", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._running = False

    def snapshot(self) -> ThermalStatus:
        with self._lock:
            return self._status

    def _run(self) -> None:
        while self._running:
            try:
                with urlopen(self.url, timeout=0.45) as response:
                    payload = json.loads(response.read().decode("utf-8"))
                status = parse_status(payload)
            except (OSError, URLError, json.JSONDecodeError, ValueError) as exc:
                # A short-lived socket error must not make an enabled sensor
                # appear to have been switched off. Keep the last known state
                # while exposing the connection problem to the viewer.
                with self._lock:
                    status = replace(self._status, online=False, error=str(exc))
            with self._lock:
                self._status = status
            time.sleep(self.interval_s)


class ThermalMjpegStream:
    def __init__(self, url: str = THERMAL_STREAM_URL) -> None:
        self.url = url
        self._lock = threading.Lock()
        self._frame: np.ndarray | None = None
        self._frame_time_s: float | None = None
        self._running = False
        self._thread: threading.Thread | None = None
        self._error: str | None = None

    def start(self) -> None:
        self._running = True
        self._thread = threading.Thread(target=self._run, name="thermal-mjpeg-stream", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._running = False

    def snapshot(self) -> tuple[np.ndarray | None, float | None, str | None]:
        with self._lock:
            frame = None if self._frame is None else self._frame.copy()
            return frame, self._frame_time_s, self._error

    def clear(self) -> None:
        with self._lock:
            self._frame = None
            self._frame_time_s = None

    def _run(self) -> None:
        while self._running:
            buffer = bytearray()
            try:
                with urlopen(self.url, timeout=1.5) as response:
                    while self._running:
                        chunk = response.read(4096)
                        if not chunk:
                            break
                        buffer.extend(chunk)
                        while True:
                            header_end = buffer.find(b"\r\n\r\n")
                            if header_end < 0:
                                if len(buffer) > 2_000_000:
                                    del buffer[:-4096]
                                break
                            header = bytes(buffer[:header_end]).decode("latin1", errors="ignore")
                            content_length = None
                            for line in header.splitlines():
                                if line.lower().startswith("content-length:"):
                                    try:
                                        content_length = int(line.split(":", 1)[1].strip())
                                    except ValueError:
                                        content_length = None
                                    break
                            if content_length is None:
                                start = buffer.find(b"\xff\xd8", header_end)
                                end = buffer.find(b"\xff\xd9", start + 2)
                                if start < 0 or end < 0:
                                    break
                                jpg = bytes(buffer[start:end + 2])
                                del buffer[:end + 2]
                            else:
                                frame_start = header_end + 4
                                frame_end = frame_start + content_length
                                if len(buffer) < frame_end:
                                    break
                                jpg = bytes(buffer[frame_start:frame_end])
                                del buffer[:frame_end]
                            if not (jpg.startswith(b"\xff\xd8") and jpg.endswith(b"\xff\xd9")):
                                with self._lock:
                                    self._error = "dropped incomplete jpeg frame"
                                continue
                            arr = np.frombuffer(jpg, dtype=np.uint8)
                            frame = cv2.imdecode(arr, cv2.IMREAD_COLOR)
                            if frame is not None:
                                with self._lock:
                                    self._frame = frame
                                    self._frame_time_s = time.monotonic()
                                    self._error = None
            except (OSError, URLError) as exc:
                with self._lock:
                    self._error = str(exc)
                time.sleep(0.7)


def _fmt_temp(value: float | None) -> str:
    return "--" if value is None else f"{value:.1f} C"


def draw_thermal_debug(frame: np.ndarray | None, status: ThermalStatus, fps: float, topic: str, stream_error: str | None = None) -> np.ndarray:
    width, height = 900, 520
    panel_x = 700
    img = np.full((height, width, 3), (13, 17, 23), dtype=np.uint8)

    cv2.rectangle(img, (0, 0), (width, 42), (118, 28, 164), -1)
    _put(img, "4. Man hinh Thermal Camera", (15, 28), 0.8, (255, 255, 255))
    cv2.line(img, (panel_x, 42), (panel_x, height), (70, 75, 85), 1)

    image_rect = (52, 92, panel_x - 48, height - 42)
    x1, y1, x2, y2 = image_rect
    cv2.rectangle(img, (x1, y1), (x2, y2), (7, 10, 28), -1)
    cv2.rectangle(img, (x1, y1), (x2, y2), (55, 62, 74), 1)

    if not status.enabled:
        frame = None
    frame_age_ms = None
    if frame is not None:
        frame_h, frame_w = frame.shape[:2]
        target_w = x2 - x1
        target_h = y2 - y1
        scale = min(target_w / max(frame_w, 1), target_h / max(frame_h, 1))
        draw_w = max(1, int(frame_w * scale))
        draw_h = max(1, int(frame_h * scale))
        resized = cv2.resize(frame, (draw_w, draw_h), interpolation=cv2.INTER_AREA)
        px = x1 + (target_w - draw_w) // 2
        py = y1 + (target_h - draw_h) // 2
        img[py:py + draw_h, px:px + draw_w] = resized
        center = (px + draw_w // 2, py + draw_h // 2)
    else:
        center = ((x1 + x2) // 2, (y1 + y2) // 2)
        if not status.enabled:
            message = "THERMAL CAMERA OFF"
        elif stream_error:
            message = "WAITING FOR THERMAL SENSOR..."
        elif status.frame_age_ms is not None and status.frame_age_ms > THERMAL_STALE_FRAME_MS:
            message = "THERMAL DATA STALE"
        else:
            message = "WAITING FOR THERMAL SENSOR..."
        text_size, _baseline = cv2.getTextSize(message, cv2.FONT_HERSHEY_SIMPLEX, 0.72, 2)
        text_x = center[0] - text_size[0] // 2
        _put(img, message, (text_x, center[1]), 0.72, (230, 235, 245), 2)

    if frame is not None:
        cv2.drawMarker(img, center, (245, 245, 245), cv2.MARKER_CROSS, 30, 2)
    if status.hotspot_detected and frame is not None:
        cv2.circle(img, center, 18, (255, 245, 180), 2)
        _put(img, _fmt_temp(status.hotspot_temp_c or status.max_temp_c), (center[0] + 22, center[1] - 12), 0.5, (255, 250, 210))
        _put(img, "HOTSPOT", (center[0] + 22, center[1] + 10), 0.42, (255, 210, 120))

    sensor_text = "OFF"
    sensor_color = (170, 175, 182)
    frame_available = frame is not None
    if status.enabled and (status.sensor_online or frame_available):
        sensor_text = "ONLINE"
        sensor_color = (105, 255, 105)
    elif status.enabled:
        sensor_text = "WAITING"
        sensor_color = (0, 210, 255)

    resolution = "--"
    if frame is not None:
        resolution = f"{frame.shape[1]}x{frame.shape[0]}"
    elif status.width and status.height:
        resolution = f"{status.width}x{status.height}"

    right_x = panel_x + 18
    _put(img, f"Topic: {topic}", (right_x, 92), 0.45, (105, 255, 105))
    _put(img, f"Sensor: {sensor_text}", (right_x, 122), 0.55, sensor_color)
    _put(img, f"Resolution: {resolution}", (right_x, 152), 0.52)
    _put(img, f"FPS: {fps:.1f}", (right_x, 178), 0.48)
    mode_label = "NATIVE GAZEBO" if status.mode == "NATIVE_GAZEBO" else "SYNTHETIC"
    _put(img, f"Mode: {mode_label}", (right_x, 200), 0.40, (120, 220, 255) if status.mode == "NATIVE_GAZEBO" else (0, 210, 255))
    _put(img, f"Frame age: {status.frame_age_ms if status.frame_age_ms is not None else '--'} ms", (right_x, 220), 0.35)
    _put(img, f"Min: {_fmt_temp(status.min_temp_c)}", (right_x, 248), 0.46)
    _put(img, f"Avg: {_fmt_temp(status.avg_temp_c)}", (right_x, 272), 0.46)
    _put(img, f"Max: {_fmt_temp(status.max_temp_c)}", (right_x, 296), 0.46, (255, 240, 190) if status.hotspot_detected else (235, 235, 235))

    if status.hotspot_detected:
        _put(img, "HOTSPOT DETECTED", (right_x, 326), 0.45, (255, 210, 120), 2)
    else:
        _put(img, "Hotspot: NO" if status.hotspot_detected is False else "Hotspot: --", (right_x, 326), 0.45)
    _put(img, f"Threshold: {THERMAL_HOTSPOT_THRESHOLD_C:.1f} C", (right_x, 348), 0.40)
    _put(img, f"Palette: {status.palette}", (right_x, 370), 0.40)
    _put(img, f"Range: {status.display_range_mode}", (right_x, 392), 0.40)
    flags = f"ISO {'ON' if status.isotherm_enabled else 'OFF'}  DBG {'ON' if status.debug_overlay_enabled else 'OFF'}"
    _put(img, flags, (right_x, 412), 0.35, (180, 210, 235))
    _put(img, "P palette  I iso  D debug  A range", (right_x, 430), 0.27, (145, 165, 190))

    bar_x = panel_x + 58
    bar_top = 446
    bar_bottom = 500
    for y in range(bar_top, bar_bottom):
        t = 1.0 - ((y - bar_top) / max(1, bar_bottom - bar_top - 1))
        color = cv2.applyColorMap(np.array([[int(t * 255)]], dtype=np.uint8), cv2.COLORMAP_INFERNO)[0, 0]
        cv2.line(img, (bar_x, y), (bar_x + 22, y), tuple(int(c) for c in color), 1)
    _put(img, f"{THERMAL_DISPLAY_MAX_C:.0f} C", (bar_x + 32, bar_top + 8), 0.42)
    _put(img, f"{THERMAL_DISPLAY_MIN_C:.0f} C", (bar_x + 32, bar_bottom), 0.42)

    if status.error:
        error_text = str(status.error).replace("<urlopen error ", "").rstrip(">")
        _put(img, f"API: {error_text[:27]}", (right_x, height - 8), 0.30, (180, 180, 255))
    return img


__all__ = [
    "THERMAL_TOPIC",
    "ThermalMjpegStream",
    "ThermalStatus",
    "ThermalStatusPoller",
    "draw_thermal_debug",
    "parse_status",
    "send_thermal_command",
]
