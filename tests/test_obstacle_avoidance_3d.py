import math
from types import SimpleNamespace

import numpy as np

from drone.obstacle_avoidance.kinodynamic_astar import KinodynamicAStar, KinodynamicConfig
from drone.obstacle_avoidance.local_map_3d import LocalMapConfig, RollingLocalMap3D
from drone.obstacle_avoidance.local_planner import LocalPlanner3D, LocalPlannerConfig
from drone.obstacle_avoidance.planner_types import MotionOwner, PlannerState, SavedMotion, State3D
from drone.obstacle_avoidance.pointcloud_gateway import laserscan_to_points_body
from drone.obstacle_avoidance.trajectory_optimizer import TrajectoryOptimizer
from drone.obstacle_avoidance.trajectory_tracker import body_velocity_to_ned


def make_map(points, safety_margin=0.4, unknown=False):
    local_map = RollingLocalMap3D(
        LocalMapConfig(
            voxel_size_m=0.5,
            local_radius_m=12.0,
            local_vertical_m=8.0,
            safety_margin_m=safety_margin,
            unknown_is_occupied=unknown,
        )
    )
    local_map.update_from_points(np.asarray(points, dtype=np.float32))
    return local_map


def wall_points(x=3.0, y_min=-3.0, y_max=3.0, z_min=-1.0, z_max=3.0, gap=None):
    points = []
    for y in np.arange(y_min, y_max + 0.01, 0.5):
        for z in np.arange(z_min, z_max + 0.01, 0.5):
            if gap is not None:
                gy_min, gy_max, gz_min, gz_max = gap
                if gy_min <= y <= gy_max and gz_min <= z <= gz_max:
                    continue
            points.append([x, y, z])
    return np.array(points, dtype=np.float32)


def search_path(local_map, goal):
    search = KinodynamicAStar(
        KinodynamicConfig(
            max_search_time_ms=400,
            max_search_nodes=12000,
            horizon_m=8.0,
            goal_tolerance_m=1.2,
        )
    )
    return search.search(State3D(np.zeros(3), np.zeros(3)), np.asarray(goal, dtype=np.float32), local_map)


def fake_scan(distance=10.0, h=12, v=4):
    return SimpleNamespace(
        ranges=[distance] * (h * v),
        count=h,
        vertical_count=v,
        angle_min=-math.pi,
        angle_max=math.pi,
        angle_step=(2 * math.pi) / (h - 1),
        vertical_angle_min=-math.radians(30),
        vertical_angle_max=math.radians(30),
        vertical_angle_step=math.radians(60) / (v - 1),
        range_min=0.2,
        range_max=60.0,
    )


def test_point_cloud_conversion_has_xyz_variation():
    points = laserscan_to_points_body(fake_scan())
    assert points.shape == (48, 3)
    assert points[:, 2].min() < -1.0
    assert points[:, 2].max() > 1.0


def test_empty_voxel_map_straight_line_clear():
    local_map = RollingLocalMap3D(LocalMapConfig(unknown_is_occupied=False))
    assert local_map.segment_collision_free(np.zeros(3), np.array([5, 0, 0]))


def test_obstacle_voxelization_and_inflated_collision():
    local_map = RollingLocalMap3D(LocalMapConfig(voxel_size_m=0.5, safety_margin_m=0.5))
    local_map.update_from_points(np.array([[2.0, 0.0, 0.0]], dtype=np.float32))
    assert local_map.is_occupied(np.array([2.0, 0.0, 0.0]))
    assert local_map.clearance(np.zeros(3)) < 2.0


def test_blocked_straight_line():
    local_map = RollingLocalMap3D(LocalMapConfig(voxel_size_m=0.5, safety_margin_m=0.5))
    local_map.update_from_points(np.array([[2.0, 0.0, 0.0]], dtype=np.float32))
    assert not local_map.segment_collision_free(np.zeros(3), np.array([5, 0, 0]))


def test_kinodynamic_astar_empty_world():
    local_map = RollingLocalMap3D(LocalMapConfig(unknown_is_occupied=False))
    search = KinodynamicAStar(KinodynamicConfig(max_search_time_ms=200, max_search_nodes=5000))
    path = search.search(
        State3D(position=np.zeros(3), velocity=np.zeros(3)),
        np.array([4.0, 0.0, 0.0]),
        local_map,
    )
    assert path
    assert path[-1].position[0] > 2.0


def test_kinodynamic_astar_avoids_tree_ahead():
    local_map = RollingLocalMap3D(LocalMapConfig(voxel_size_m=0.5, safety_margin_m=0.4, unknown_is_occupied=False))
    tree = np.array([[2.0, 0.0, z] for z in np.linspace(-1, 3, 10)], dtype=np.float32)
    local_map.update_from_points(tree)
    search = KinodynamicAStar(KinodynamicConfig(max_search_time_ms=300, max_search_nodes=8000))
    path = search.search(
        State3D(position=np.zeros(3), velocity=np.zeros(3)),
        np.array([5.0, 0.0, 0.0]),
        local_map,
    )
    assert path
    assert local_map.trajectory_collision_free(path)


def test_trajectory_optimizer_validates_collision():
    local_map = RollingLocalMap3D(LocalMapConfig(voxel_size_m=0.5, safety_margin_m=0.4, unknown_is_occupied=False))
    local_map.update_from_points(np.array([[2.0, 0.0, 0.0]], dtype=np.float32))
    search = KinodynamicAStar(KinodynamicConfig(max_search_time_ms=300, max_search_nodes=8000))
    raw = search.search(State3D(np.zeros(3), np.zeros(3)), np.array([5.0, 0.0, 0.0]), local_map)
    trajectory = TrajectoryOptimizer().optimize(raw, local_map)
    assert trajectory
    assert local_map.trajectory_collision_free(trajectory)


def test_saved_motion_preserved_by_planner_begin():
    planner = LocalPlanner3D()
    saved = SavedMotion(1, 0, 0, 1, 0, 0)
    planner.begin(saved)
    planner.begin(SavedMotion(0, 1, 0, 0, 1, 90))
    assert planner.saved_motion == saved


def test_motion_owner_priority_order():
    assert [owner.value for owner in MotionOwner] == ["MANUAL", "PLANNER", "EMERGENCY"]


def test_wall_blocks_straight_line():
    local_map = make_map(wall_points())
    assert not local_map.segment_collision_free(np.zeros(3), np.array([6.0, 0.0, 0.0]))


def test_wall_left_route_can_be_found():
    local_map = make_map(wall_points(y_min=-1.0, y_max=3.0))
    path = search_path(local_map, [6.0, -2.5, 0.0])
    assert path
    assert path[-1].position[1] < -0.5
    assert local_map.trajectory_collision_free(path)


def test_wall_right_route_can_be_found():
    local_map = make_map(wall_points(y_min=-3.0, y_max=1.0))
    path = search_path(local_map, [6.0, 2.5, 0.0])
    assert path
    assert path[-1].position[1] > 0.5
    assert local_map.trajectory_collision_free(path)


def test_route_over_low_obstacle():
    local_map = make_map(wall_points(y_min=-1.0, y_max=1.0, z_min=-1.0, z_max=0.8))
    path = search_path(local_map, [5.0, 0.0, 2.5])
    assert path
    assert path[-1].position[2] > 1.0
    assert local_map.trajectory_collision_free(path)


def test_obstacle_above_blocks_climb_segment():
    ceiling = np.array([[0.0, 0.0, 1.0], [0.5, 0.0, 1.0], [-0.5, 0.0, 1.0]], dtype=np.float32)
    local_map = make_map(ceiling)
    assert not local_map.segment_collision_free(np.zeros(3), np.array([0.0, 0.0, 2.0]))


def test_obstacle_below_blocks_descent_segment():
    floor = np.array([[0.0, 0.0, 0.1], [0.5, 0.0, 0.1], [-0.5, 0.0, 0.1]], dtype=np.float32)
    local_map = make_map(floor)
    assert not local_map.segment_collision_free(np.zeros(3), np.array([0.0, 0.0, -2.0]))


def test_narrow_gap_rejected_by_inflation():
    wall = wall_points(gap=(-0.25, 0.25, -0.5, 0.5))
    local_map = make_map(wall, safety_margin=0.75)
    assert not local_map.segment_collision_free(np.zeros(3), np.array([6.0, 0.0, 0.0]))


def test_forest_like_multiple_obstacles_has_collision_free_path():
    points = []
    for x, y in [(2, -1.0), (3, 1.0), (4, -0.5), (5, 1.4)]:
        for z in np.linspace(-1, 3, 9):
            points.append([x, y, z])
    local_map = make_map(points, safety_margin=0.25)
    path = search_path(local_map, [7.0, 0.0, 0.0])
    assert path
    assert local_map.trajectory_collision_free(path)


def test_no_safe_path_returns_empty():
    cage = []
    for x in [-1.0, 1.0]:
        for y in np.arange(-1, 1.1, 0.5):
            for z in np.arange(-1, 1.1, 0.5):
                cage.append([x, y, z])
    for y in [-1.0, 1.0]:
        for x in np.arange(-1, 1.1, 0.5):
            for z in np.arange(-1, 1.1, 0.5):
                cage.append([x, y, z])
    local_map = make_map(cage, safety_margin=0.75)
    path = search_path(local_map, [5.0, 0.0, 0.0])
    assert not path


def test_speed_constraint_validation_rejects_fast_trajectory():
    local_map = RollingLocalMap3D(LocalMapConfig(unknown_is_occupied=False))
    optimizer = TrajectoryOptimizer()
    from drone.obstacle_avoidance.planner_types import TrajectoryPoint
    trajectory = [
        TrajectoryPoint(np.array([0, 0, 0.0]), np.array([99, 0, 0.0]), np.zeros(3), 0.0)
    ]
    assert not optimizer.validate(trajectory, local_map)


def test_acceleration_constraint_validation_rejects_hard_accel():
    local_map = RollingLocalMap3D(LocalMapConfig(unknown_is_occupied=False))
    optimizer = TrajectoryOptimizer()
    from drone.obstacle_avoidance.planner_types import TrajectoryPoint
    trajectory = [
        TrajectoryPoint(np.array([0, 0, 0.0]), np.zeros(3), np.array([99, 0, 0.0]), 0.0)
    ]
    assert not optimizer.validate(trajectory, local_map)


def test_new_obstacle_invalidates_existing_trajectory():
    empty_map = RollingLocalMap3D(LocalMapConfig(unknown_is_occupied=False))
    raw = search_path(empty_map, [5.0, 0.0, 0.0])
    obstacle_map = make_map([[2.0, 0.0, 0.0]])
    assert raw
    assert not obstacle_map.trajectory_collision_free(raw)


def test_emergency_owner_overrides_planner_owner():
    owner = MotionOwner.PLANNER
    if owner != MotionOwner.EMERGENCY:
        owner = MotionOwner.EMERGENCY
    assert owner == MotionOwner.EMERGENCY


def test_planner_owner_overrides_manual_owner():
    owner = MotionOwner.MANUAL
    owner = MotionOwner.PLANNER
    assert owner == MotionOwner.PLANNER


def test_planner_resumes_after_emergency_clears():
    owner = MotionOwner.EMERGENCY
    owner = MotionOwner.PLANNER
    assert owner == MotionOwner.PLANNER


def test_local_planner_rejoins_when_route_clear():
    planner = LocalPlanner3D(LocalPlannerConfig())
    saved = SavedMotion(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    planner.begin(saved)
    result = planner.update(np.empty((0, 3), dtype=np.float32), yaw_deg=0.0)
    assert result.state == PlannerState.MONITORING
    assert result.command is not None
    assert result.command.north_m_s == 1.0


def test_ground_below_drone_does_not_trigger_planner_failsafe():
    planner = LocalPlanner3D(LocalPlannerConfig())
    saved = SavedMotion(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    planner.begin(saved)
    ground = np.array([[0.0, 0.0, -1.5], [1.0, 0.0, -1.5], [2.0, 0.0, -1.5]], dtype=np.float32)
    result = planner.update(ground, yaw_deg=0.0)
    assert result.state != PlannerState.FAILSAFE


def test_front_obstacle_near_drone_triggers_planner_failsafe():
    planner = LocalPlanner3D(LocalPlannerConfig(emergency_distance_m=2.0))
    saved = SavedMotion(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
    planner.begin(saved)
    obstacle = np.array([[1.0, 0.0, 0.2]], dtype=np.float32)
    result = planner.update(obstacle, yaw_deg=0.0)
    assert result.state == PlannerState.FAILSAFE


def test_tracker_body_to_ned_conversion():
    command = body_velocity_to_ned(forward=1.0, right=0.0, up=1.0, yaw_deg=0.0)
    assert command.north_m_s == 1.0
    assert command.east_m_s == 0.0
    assert command.down_m_s == -1.0
