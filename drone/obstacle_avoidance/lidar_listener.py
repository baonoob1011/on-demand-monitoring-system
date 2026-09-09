import os
import subprocess
from datetime import datetime

from sensor_reader import (
    EMERGENCY_DISTANCE_M,
    OBSTACLE_DISTANCE_M,
    WARNING_DISTANCE_M,
    build_obstacle_state,
    front_obstacle_reading,
)


LIDAR_TOPIC = "/lidar"
EXPECTED_SCAN_SIZE = 360
SIGNIFICANT_DISTANCE_CHANGE_M = 0.5
LOG_FORMAT = os.getenv("LIDAR_LOG_FORMAT", "detail").strip().lower()


def get_safety_action(status: str) -> str:
    if status == "EMERGENCY":
        return "EMERGENCY_HOVER"
    if status == "OBSTACLE":
        return "HOVER"
    if status == "WARNING":
        return "MONITOR"
    return "CONTINUE"


def should_log_scan(
    last_status: str | None,
    last_direction: str | None,
    last_distance: float | None,
    status: str,
    direction: str,
    distance: float,
) -> bool:
    if status == "CLEAR":
        return False

    if last_status != status:
        return True

    if last_direction != direction:
        return True

    if last_distance is None or abs(last_distance - distance) >= SIGNIFICANT_DISTANCE_CHANGE_M:
        return True

    return False


def print_detailed_scan(state, status: str, direction: str, distance: float, action: str) -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

    print(
        f"[LIDAR][{timestamp}]\n"
        f"STATUS     : {status}\n"
        f"NEAREST    : {direction} @ {distance:.2f}m\n"
        f"FRONT      : {state.front:.2f}m\n"
        f"FRONT_LEFT : {state.front_left:.2f}m\n"
        f"FRONT_RIGHT: {state.front_right:.2f}m\n"
        f"LEFT       : {state.left:.2f}m\n"
        f"RIGHT      : {state.right:.2f}m\n"
        f"THRESHOLDS : "
        f"warning={WARNING_DISTANCE_M:.2f}m | "
        f"obstacle={OBSTACLE_DISTANCE_M:.2f}m | "
        f"emergency={EMERGENCY_DISTANCE_M:.2f}m\n"
        f"ACTION     : {action}"
    )


def print_compact_scan(state, status: str, direction: str, distance: float, action: str) -> None:
    print(
        f"[LIDAR] "
        f"STATUS={status} | "
        f"NEAREST={direction} {distance:.2f}m | "
        f"F={state.front:.2f} | "
        f"FL={state.front_left:.2f} | "
        f"FR={state.front_right:.2f} | "
        f"L={state.left:.2f} | "
        f"R={state.right:.2f} | "
        f"ACTION={action}"
    )


def print_scan(state, status: str, direction: str, distance: float, action: str) -> None:
    if LOG_FORMAT == "compact":
        print_compact_scan(state, status, direction, distance, action)
    elif LOG_FORMAT == "both":
        print_detailed_scan(state, status, direction, distance, action)
        print_compact_scan(state, status, direction, distance, action)
    else:
        print_detailed_scan(state, status, direction, distance, action)


def parse_range_value(value: str) -> float | None:
    if value == "inf":
        return float("inf")

    try:
        return float(value)
    except ValueError:
        return None


def read_lidar():
    process = subprocess.Popen(
        ["gz", "topic", "-e", "-t", LIDAR_TOPIC],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1,
    )

    ranges = []
    last_status = None
    last_direction = None
    last_distance = None

    print("[LIDAR] Listening on /lidar...")

    for line in process.stdout:
        line = line.strip()

        if line.startswith("ranges:"):
            value = parse_range_value(line.split(":", 1)[1].strip())
            if value is not None:
                ranges.append(value)

        if len(ranges) == EXPECTED_SCAN_SIZE:
            state = build_obstacle_state(ranges)
            status, direction, distance = front_obstacle_reading(state)
            action = get_safety_action(status)

            if status == "CLEAR":
                if last_status in ("WARNING", "OBSTACLE", "EMERGENCY"):
                    print("[LIDAR] CLEAR - obstacle no longer detected")
                last_status = status
                last_direction = direction
                last_distance = distance
                ranges.clear()
                continue

            if should_log_scan(
                last_status,
                last_direction,
                last_distance,
                status,
                direction,
                distance,
            ):
                print_scan(state, status, direction, distance, action)
                last_status = status
                last_direction = direction
                last_distance = distance

            ranges.clear()


if __name__ == "__main__":
    read_lidar()
