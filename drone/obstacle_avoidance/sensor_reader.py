from dataclasses import dataclass
import math
import os


# ============================================================
# CONFIG
# ============================================================

MAX_RANGE_M = float(os.getenv("LIDAR_MAX_RANGE_M", "60.0"))
MIN_VALID_DISTANCE_M = float(os.getenv("LIDAR_MIN_VALID_DISTANCE_M", "1.0"))

WARNING_DISTANCE_M = float(os.getenv("LIDAR_WARNING_DISTANCE_M", "25.0"))
OBSTACLE_DISTANCE_M = float(os.getenv("LIDAR_OBSTACLE_DISTANCE_M", "15.0"))
EMERGENCY_DISTANCE_M = float(os.getenv("LIDAR_EMERGENCY_DISTANCE_M", "7.0"))


# ============================================================
# DATA MODELS
# ============================================================

@dataclass
class SectorReading:
    distance: float
    angle_deg: float


@dataclass
class ObstacleState:
    front: float
    front_left: float
    front_right: float
    left: float
    right: float
    back_left: float = MAX_RANGE_M
    back: float = MAX_RANGE_M
    back_right: float = MAX_RANGE_M

    front_angle: float = 0.0
    front_left_angle: float = 45.0
    front_right_angle: float = -45.0
    left_angle: float = 90.0
    right_angle: float = -90.0
    back_left_angle: float = 135.0
    back_angle: float = 180.0
    back_right_angle: float = -135.0

    nearest_distance: float = MAX_RANGE_M
    nearest_angle: float = 0.0
    nearest_direction: str = "NONE"


# ============================================================
# BASIC HELPERS
# ============================================================

def sanitize(value: float) -> float:
    """
    inf / nan nghĩa là tia LiDAR không gặp vật cản
    trong phạm vi MAX_RANGE_M.
    """

    if math.isinf(value) or math.isnan(value):
        return MAX_RANGE_M

    if value < MIN_VALID_DISTANCE_M:
        return MAX_RANGE_M

    return min(value, MAX_RANGE_M)


def index_to_angle(
        index: int,
        sample_count: int,
) -> float:
    """
    Chuyển index LiDAR sang góc.

    Quy ước:
        0°      = FRONT
        +45°    = FRONT_LEFT
        -45°    = FRONT_RIGHT
        +90°    = LEFT
        -90°    = RIGHT
        ±180°   = BACK
    """

    if sample_count <= 0:
        return 0.0

    step = 360.0 / sample_count

    return -180.0 + index * step


# ============================================================
# SECTOR READING
# ============================================================

def sector_reading(
        ranges: list[float],
        center_deg: float,
        width_deg: float = 20.0,
) -> SectorReading:
    """
    Tìm tia LiDAR gần nhất trong một sector.

    Không chỉ trả distance mà còn trả góc chính xác
    của vật cản gần nhất trong sector đó.
    """

    if not ranges:
        return SectorReading(
            distance=MAX_RANGE_M,
            angle_deg=center_deg,
        )

    start_deg = center_deg - width_deg / 2.0
    end_deg = center_deg + width_deg / 2.0

    best_distance = MAX_RANGE_M
    best_angle = center_deg

    for index, raw_value in enumerate(ranges):
        angle = index_to_angle(index, len(ranges))

        if start_deg <= angle <= end_deg:
            distance = sanitize(raw_value)

            if distance < best_distance:
                best_distance = distance
                best_angle = angle

    return SectorReading(
        distance=best_distance,
        angle_deg=best_angle,
    )


def sector_min(
        ranges: list[float],
        center_deg: float,
        width_deg: float = 20.0,
) -> float:
    """
    Giữ lại function cũ để tương thích code hiện tại.
    """

    return sector_reading(
        ranges,
        center_deg,
        width_deg,
    ).distance


# ============================================================
# FULL 360° NEAREST OBJECT
# ============================================================

def find_nearest_360(
        ranges: list[float],
) -> tuple[float, float]:
    """
    Tìm vật cản gần nhất trên TOÀN BỘ vòng quét 360°.

    Returns:
        distance_m
        angle_deg
    """

    if not ranges:
        return MAX_RANGE_M, 0.0

    nearest_dist = MAX_RANGE_M
    nearest_angle = 0.0

    for index, raw_value in enumerate(ranges):
        distance = sanitize(raw_value)

        if distance < nearest_dist:
            nearest_dist = distance
            nearest_angle = index_to_angle(
                index,
                len(ranges),
            )

    return nearest_dist, nearest_angle


# ============================================================
# ANGLE -> HUMAN DIRECTION
# ============================================================

def angle_to_direction(angle_deg: float) -> str:
    """
    Chuyển góc LiDAR thành hướng dễ đọc.

    0°        FRONT
    +         LEFT
    -         RIGHT
    ±180°     BACK
    """

    angle = angle_deg

    if -15 <= angle <= 15:
        return "FRONT"

    if 15 < angle <= 67.5:
        return "FRONT_LEFT"

    if -67.5 <= angle < -15:
        return "FRONT_RIGHT"

    if 67.5 < angle <= 112.5:
        return "LEFT"

    if -112.5 <= angle < -67.5:
        return "RIGHT"

    if 112.5 < angle < 157.5:
        return "BACK_LEFT"

    if -157.5 < angle < -112.5:
        return "BACK_RIGHT"

    return "BACK"


def direction_vietnamese(direction: str) -> str:
    names = {
        "FRONT": "PHÍA TRƯỚC",
        "FRONT_LEFT": "TRƯỚC BÊN TRÁI",
        "FRONT_RIGHT": "TRƯỚC BÊN PHẢI",
        "LEFT": "BÊN TRÁI",
        "RIGHT": "BÊN PHẢI",
        "BACK_LEFT": "SAU BÊN TRÁI",
        "BACK_RIGHT": "SAU BÊN PHẢI",
        "BACK": "PHÍA SAU",
        "NONE": "KHÔNG XÁC ĐỊNH",
    }

    return names.get(
        direction,
        direction,
    )


# ============================================================
# BUILD STATE
# ============================================================

def build_obstacle_state(
        ranges: list[float],
) -> ObstacleState:

    front = sector_reading(
        ranges,
        0.0,
        20.0,
    )

    front_left = sector_reading(
        ranges,
        45.0,
        20.0,
    )

    front_right = sector_reading(
        ranges,
        -45.0,
        20.0,
    )

    left = sector_reading(
        ranges,
        90.0,
        20.0,
    )

    right = sector_reading(
        ranges,
        -90.0,
        20.0,
    )

    back_left = sector_reading(
        ranges,
        135.0,
        20.0,
    )

    back = sector_reading(
        ranges,
        180.0,
        20.0,
    )

    back_right = sector_reading(
        ranges,
        -135.0,
        20.0,
    )

    nearest_dist, nearest_angle = find_nearest_360(
        ranges
    )

    nearest_dir = angle_to_direction(
        nearest_angle
    )

    return ObstacleState(
        front=front.distance,
        front_left=front_left.distance,
        front_right=front_right.distance,
        left=left.distance,
        right=right.distance,
        back_left=back_left.distance,
        back=back.distance,
        back_right=back_right.distance,

        front_angle=front.angle_deg,
        front_left_angle=front_left.angle_deg,
        front_right_angle=front_right.angle_deg,
        left_angle=left.angle_deg,
        right_angle=right.angle_deg,
        back_left_angle=back_left.angle_deg,
        back_angle=back.angle_deg,
        back_right_angle=back_right.angle_deg,

        nearest_distance=nearest_dist,
        nearest_angle=nearest_angle,
        nearest_direction=nearest_dir,
    )


# ============================================================
# DETECTION
# ============================================================

def nearest_distance(
        state: ObstacleState,
) -> float:
    """
    Khoảng cách vật cản gần nhất.

    Ưu tiên kết quả full 360°.
    """

    return state.nearest_distance


def front_obstacle_reading(
        state: ObstacleState,
) -> tuple[str, str, float]:
    direction = "FRONT"
    distance = state.front

    if distance <= EMERGENCY_DISTANCE_M:
        return "EMERGENCY", direction, distance

    if distance <= OBSTACLE_DISTANCE_M:
        return "OBSTACLE", direction, distance

    if distance <= WARNING_DISTANCE_M:
        return "WARNING", direction, distance

    return "CLEAR", "FRONT", distance


def detect_obstacle(
        state: ObstacleState,
) -> str:
    """
    <= EMERGENCY_DISTANCE_M  EMERGENCY
    <= OBSTACLE_DISTANCE_M   OBSTACLE
    <= WARNING_DISTANCE_M    WARNING
    > WARNING_DISTANCE_M     CLEAR
    """

    distance = nearest_distance(state)

    if distance <= EMERGENCY_DISTANCE_M:
        return "EMERGENCY"

    if distance <= OBSTACLE_DISTANCE_M:
        return "OBSTACLE"

    if distance <= WARNING_DISTANCE_M:
        return "WARNING"

    return "CLEAR"


def get_obstacle_direction(
        state: ObstacleState,
) -> str:
    return state.nearest_direction


# ============================================================
# STATUS / ACTION
# ============================================================

def get_safety_action(
        status: str,
) -> str:

    if status == "EMERGENCY":
        return "EMERGENCY_HOVER"

    if status == "OBSTACLE":
        return "HOVER"

    if status == "WARNING":
        return "MONITOR"

    return "CONTINUE"


# ============================================================
# DETAILED LOG
# ============================================================

def format_obstacle_log(
        state: ObstacleState,
        status: str,
) -> str:
    """
    Tạo log cực chi tiết cho terminal.
    """

    direction = get_obstacle_direction(state)
    direction_vi = direction_vietnamese(direction)

    action = get_safety_action(status)

    distance = state.nearest_distance
    angle = state.nearest_angle

    # Góc dương = bên trái
    # Góc âm = bên phải
    if angle > 0:
        angle_side = "LEFT"
    elif angle < 0:
        angle_side = "RIGHT"
    else:
        angle_side = "CENTER"

    return (
        "\n"
        "============================================================\n"
        "[LIDAR] OBSTACLE DETECTION\n"
        "============================================================\n"
        f"STATUS           : {status}\n"
        f"ACTION           : {action}\n"
        "\n"
        "---------------- NEAREST OBSTACLE --------------------------\n"
        f"DIRECTION        : {direction}\n"
        f"DIRECTION_VI     : {direction_vi}\n"
        f"DISTANCE         : {distance:.2f} m\n"
        f"ANGLE            : {angle:+.2f} deg\n"
        f"ANGLE_SIDE       : {angle_side}\n"
        "\n"
        "---------------- SECTOR DISTANCES --------------------------\n"
        f"FRONT            : {state.front:.2f} m"
        f" @ {state.front_angle:+.2f} deg\n"
        f"FRONT_LEFT       : {state.front_left:.2f} m"
        f" @ {state.front_left_angle:+.2f} deg\n"
        f"FRONT_RIGHT      : {state.front_right:.2f} m"
        f" @ {state.front_right_angle:+.2f} deg\n"
        f"LEFT             : {state.left:.2f} m"
        f" @ {state.left_angle:+.2f} deg\n"
        f"RIGHT            : {state.right:.2f} m"
        f" @ {state.right_angle:+.2f} deg\n"
        "\n"
        "---------------- THRESHOLDS --------------------------------\n"
        f"WARNING          : <= {WARNING_DISTANCE_M:.2f} m\n"
        f"OBSTACLE         : <= {OBSTACLE_DISTANCE_M:.2f} m\n"
        f"EMERGENCY        : <= {EMERGENCY_DISTANCE_M:.2f} m\n"
        f"LIDAR MAX RANGE  : {MAX_RANGE_M:.2f} m\n"
        "============================================================"
    )
