import importlib
import subprocess
import threading

from .sensor_reader import (
    ObstacleState,
    build_obstacle_state,
    front_obstacle_reading,
)


LIDAR_TOPIC = "/lidar"
EXPECTED_SCAN_SIZE = 360


def load_gazebo_transport():
    last_error = None

    for module_name in ("gz.transport13",):
        try:
            module = importlib.import_module(module_name)
            return module.Node, None
        except ImportError as exc:
            last_error = exc

    return None, last_error


def load_laser_scan_type():
    last_error = None

    for module_name in (
        "gz.msgs10.laserscan_pb2",
        "gz.msgs10.laser_scan_pb2",
    ):
        try:
            module = importlib.import_module(module_name)
            if hasattr(module, "LaserScan"):
                return module.LaserScan, None
        except ImportError as exc:
            last_error = exc

    return None, last_error


Node, NODE_IMPORT_ERROR = load_gazebo_transport()
LaserScan, LASER_SCAN_IMPORT_ERROR = load_laser_scan_type()


def native_import_error() -> Exception | str | None:
    if Node is None:
        return NODE_IMPORT_ERROR or "gz.transport13.Node not found"
    if LaserScan is None:
        return LASER_SCAN_IMPORT_ERROR or "LaserScan protobuf type not found"
    return None


def parse_range_value(value: str) -> float | None:
    if value == "inf":
        return float("inf")

    try:
        return float(value)
    except ValueError:
        return None


class LidarGateway:
    def __init__(self):
        self.node = None
        self.process: subprocess.Popen | None = None
        self.thread: threading.Thread | None = None
        self.lock = threading.Lock()

        self.available = False
        self.latest_state: ObstacleState | None = None
        self.latest_status = "CLEAR"
        self.latest_direction = "NONE"

    def start(self):
        if self._start_native():
            return

        if self._start_subprocess():
            return

        print("[LIDAR] Unavailable - obstacle avoidance disabled")

    def _start_native(self) -> bool:
        import_error = native_import_error()
        if import_error is not None:
            print(f"[LIDAR] Native Gazebo transport unavailable: {import_error}")
            return False

        try:
            self.node = Node()
            self.node.subscribe(
                LaserScan,
                LIDAR_TOPIC,
                self._on_scan,
            )
        except Exception as exc:
            print(f"[LIDAR] Native Gazebo transport unavailable: {exc}")
            return False

        self.available = True
        print(f"[LIDAR] Listening: {LIDAR_TOPIC} (native mode)")
        return True

    def _start_subprocess(self) -> bool:
        print(f"[LIDAR] Falling back to gz topic {LIDAR_TOPIC}")

        try:
            self.process = subprocess.Popen(
                ["gz", "topic", "-e", "-t", LIDAR_TOPIC],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                bufsize=1,
            )
        except Exception as exc:
            print(f"[LIDAR] Subprocess fallback failed: {exc}")
            return False

        self.available = True
        self.thread = threading.Thread(target=self._read_subprocess, daemon=True)
        self.thread.start()
        print(f"[LIDAR] Listening: {LIDAR_TOPIC} (subprocess mode)")
        return True

    def _on_scan(self, msg: LaserScan, *_args):
        self._update_ranges(list(msg.ranges))

    def _read_subprocess(self) -> None:
        if self.process is None or self.process.stdout is None:
            self.available = False
            return

        ranges = []

        for line in self.process.stdout:
            line = line.strip()

            if line.startswith("ranges:"):
                value = parse_range_value(line.split(":", 1)[1].strip())
                if value is not None:
                    ranges.append(value)

            if len(ranges) == EXPECTED_SCAN_SIZE:
                self._update_ranges(ranges)
                ranges = []

        self.available = False

    def _update_ranges(self, ranges: list[float]) -> None:
        if not ranges:
            return

        state = build_obstacle_state(ranges)
        status, direction, _distance = front_obstacle_reading(state)

        with self.lock:
            self.latest_state = state
            self.latest_status = status
            self.latest_direction = direction

    def snapshot(self):
        with self.lock:
            return (
                self.latest_state,
                self.latest_status,
                self.latest_direction,
            )
