from __future__ import annotations

from dataclasses import dataclass
import math
import os
import re
import subprocess
import threading
import time
from typing import Callable

import numpy as np

from drone.obstacle_avoidance.pointcloud_gateway import laserscan_to_points_body


RANGE_RE = re.compile(r"^ranges:\s*(.+)$")
FLOAT_RE = re.compile(r"^([a-zA-Z_]+):\s*(-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?|-?inf|nan)$")


def _parse_float(value: str) -> float | None:
    value = value.strip().strip('"')
    if value in {"inf", "+inf"}:
        return float("inf")
    if value == "-inf":
        return float("-inf")
    if value == "nan":
        return float("nan")
    try:
        return float(value)
    except ValueError:
        return None


@dataclass(frozen=True)
class LaserScanSample:
    ranges: tuple[float, ...]
    count: int
    vertical_count: int
    angle_min: float
    angle_max: float
    angle_step: float
    vertical_angle_min: float
    vertical_angle_max: float
    vertical_angle_step: float
    range_min: float
    range_max: float
    received_at: float

    def to_pointcloud(self) -> np.ndarray:
        msg = type("LaserScanMessage", (), {})()
        msg.ranges = self.ranges
        msg.count = self.count
        msg.vertical_count = self.vertical_count
        msg.angle_min = self.angle_min
        msg.angle_max = self.angle_max
        msg.angle_step = self.angle_step
        msg.vertical_angle_min = self.vertical_angle_min
        msg.vertical_angle_max = self.vertical_angle_max
        msg.vertical_angle_step = self.vertical_angle_step
        msg.range_min = self.range_min
        msg.range_max = self.range_max
        return laserscan_to_points_body(msg)


class LatestValue:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._value = None
        self._seq = 0

    def set(self, value) -> None:
        with self._lock:
            self._seq += 1
            self._value = value

    def get(self):
        with self._lock:
            return self._seq, self._value


class GazeboLaserScanStream:
    """Newest-sample reader for `gz topic -e` LaserScan text output."""

    def __init__(
        self,
        topic: str,
        expected_scan_size: int,
        label: str,
        on_error: Callable[[str], None] | None = None,
    ) -> None:
        self.topic = topic
        self.expected_scan_size = expected_scan_size
        self.label = label
        self.on_error = on_error or (lambda _message: None)
        self.latest = LatestValue()
        self.process: subprocess.Popen | None = None
        self.thread: threading.Thread | None = None
        self.running = False

    def start(self) -> None:
        self.running = True
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def stop(self) -> None:
        self.running = False
        if self.process is not None and self.process.poll() is None:
            self.process.terminate()

    def snapshot(self) -> tuple[int, LaserScanSample | None]:
        return self.latest.get()

    def _run(self) -> None:
        while self.running:
            try:
                self.process = subprocess.Popen(
                    ["gz", "topic", "-e", "-t", self.topic],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    bufsize=1,
                )
            except Exception as exc:
                self.on_error(f"{self.label}: cannot start gz topic: {exc}")
                time.sleep(2.0)
                continue

            try:
                self._read_process()
            finally:
                if self.process is not None and self.process.poll() is None:
                    self.process.terminate()

            if self.running:
                self.on_error(f"{self.label}: stream ended, reconnecting")
                time.sleep(1.0)

    def _read_process(self) -> None:
        if self.process is None or self.process.stdout is None:
            return

        fields = {
            "count": 0.0,
            "vertical_count": 1.0,
            "angle_min": -math.pi,
            "angle_max": math.pi,
            "angle_step": 0.0,
            "vertical_angle_min": 0.0,
            "vertical_angle_max": 0.0,
            "vertical_angle_step": 0.0,
            "range_min": 0.0,
            "range_max": float(os.getenv("LIDAR_MAX_RANGE_M", "120.0")),
        }
        ranges: list[float] = []

        for raw_line in self.process.stdout:
            if not self.running:
                return

            line = raw_line.strip()
            range_match = RANGE_RE.match(line)
            if range_match:
                value = _parse_float(range_match.group(1))
                if value is not None:
                    ranges.append(value)
                sample = self._maybe_build_sample(fields, ranges)
                if sample is not None:
                    self.latest.set(sample)
                    fields["count"] = 0.0
                    fields["vertical_count"] = 1.0
                    ranges = []
                continue

            float_match = FLOAT_RE.match(line)
            if float_match:
                name, raw_value = float_match.groups()
                value = _parse_float(raw_value)
                if value is not None and name in fields:
                    fields[name] = value

    def _maybe_build_sample(self, fields: dict[str, float], ranges: list[float]) -> LaserScanSample | None:
        horizontal_count = int(fields.get("count") or 0)
        vertical_count = int(fields.get("vertical_count") or 1)
        expected = horizontal_count * max(vertical_count, 1)
        if expected <= 0:
            expected = self.expected_scan_size

        if len(ranges) < expected:
            return None

        usable = tuple(ranges[:expected])
        return LaserScanSample(
            ranges=usable,
            count=horizontal_count or len(usable),
            vertical_count=vertical_count or 1,
            angle_min=float(fields["angle_min"]),
            angle_max=float(fields["angle_max"]),
            angle_step=float(fields["angle_step"]),
            vertical_angle_min=float(fields["vertical_angle_min"]),
            vertical_angle_max=float(fields["vertical_angle_max"]),
            vertical_angle_step=float(fields["vertical_angle_step"]),
            range_min=float(fields["range_min"]),
            range_max=float(fields["range_max"]),
            received_at=time.monotonic(),
        )


def topic_list() -> list[str]:
    try:
        result = subprocess.run(
            ["gz", "topic", "-l"],
            check=False,
            capture_output=True,
            text=True,
            timeout=4.0,
        )
    except Exception:
        return []
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]

