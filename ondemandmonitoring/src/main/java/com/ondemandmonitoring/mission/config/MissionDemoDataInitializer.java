package com.ondemandmonitoring.mission.config;

import java.sql.Date;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionDemoDataInitializer {

    static final String CUSTOMER_ID = "00000000-0000-0000-0000-000000000001";
    static final String OPERATOR_ID = "OPR-112";
    static final String SERVICE_ID = "30000000-0000-0000-0000-000000000001";
    static final String DRONE_MODEL_ID = "30000000-0000-0000-0000-000000000003";
    static final String DRONE_PAYLOAD_ID = "30000000-0000-0000-0000-000000000004";
    static final String DRONE_ID = "30000000-0000-0000-0000-000000000005";
    static final String ORDER_ID = "ORD-78234";
    static final String MISSION_ID = "MSN-2024-0891";
    static final String MISSION_PLAN_ID = "FP-2024-0891-A";

    JdbcTemplate jdbcTemplate;

    @Bean
    @Order(40)
    ApplicationRunner seedMissionDemoData() {
        return args -> {
            seedService();
            seedPreferredTime();
            seedDrone();
            seedOrder();
            seedMission();
            seedAssignments();
            seedPlan();
            seedWaypoints();
        };
    }

    private void seedService() {
        jdbcTemplate.update("""
                INSERT INTO category_services (id, name, description, created_at, updated_at, version)
                VALUES (?, 'Giám sát môi trường', 'Forest monitoring demo service for OMSS mission control.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    description = EXCLUDED.description,
                    updated_at = CURRENT_TIMESTAMP
                """, SERVICE_ID);
    }

    private void seedPreferredTime() {
        jdbcTemplate.update("""
                INSERT INTO preferred_times (id, code, name, start_time, end_time, created_at, updated_at, version)
                VALUES (?, 'MORNING', 'Morning', '07:00:00', '11:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (code) DO UPDATE SET
                    name = EXCLUDED.name,
                    start_time = EXCLUDED.start_time,
                    end_time = EXCLUDED.end_time,
                    updated_at = CURRENT_TIMESTAMP
                """, "30000000-0000-0000-0000-000000000002");
    }

    private void seedDrone() {
        jdbcTemplate.update("""
                INSERT INTO drone_models (
                    id, model_code, manufacturer, category, max_flight_time_minutes,
                    max_takeoff_weight_kg, max_flight_altitude_meters,
                    max_wind_resistance_meters_per_second, ip_rating, specs_metadata,
                    created_at, updated_at, version
                )
                VALUES (?, 'X500-SITL', 'PX4', 'Simulation', 45, 2.5, 120, 12, 'SIM', '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (model_code) DO UPDATE SET
                    manufacturer = EXCLUDED.manufacturer,
                    category = EXCLUDED.category,
                    updated_at = CURRENT_TIMESTAMP
                """, DRONE_MODEL_ID);

        jdbcTemplate.update("""
                INSERT INTO drone_payloads (
                    id, model_name, sensor_type, weight_kg, payload_capabilities,
                    created_at, updated_at, version
                )
                VALUES (?, 'Front RGB + Thermal', 'RGB_THERMAL', 0.45, 'FPV, thermal hotspot, LiDAR preview', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (id) DO UPDATE SET
                    model_name = EXCLUDED.model_name,
                    sensor_type = EXCLUDED.sensor_type,
                    payload_capabilities = EXCLUDED.payload_capabilities,
                    updated_at = CURRENT_TIMESTAMP
                """, DRONE_PAYLOAD_ID);

        jdbcTemplate.update("""
                INSERT INTO drones (
                    id, drone_code, drone_name, serial_number, model_id, payload_id,
                    status, last_seen_at, created_at, updated_at, version
                )
                VALUES (
                    ?, 'DRN-0047', 'Eagle-47', 'SIM-X500-DRN-0047',
                    (SELECT id FROM drone_models WHERE model_code = 'X500-SITL'),
                    ?, 'PREFLIGHT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                ON CONFLICT (drone_code) DO UPDATE SET
                    drone_name = EXCLUDED.drone_name,
                    serial_number = EXCLUDED.serial_number,
                    model_id = EXCLUDED.model_id,
                    payload_id = EXCLUDED.payload_id,
                    status = EXCLUDED.status,
                    last_seen_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """, DRONE_ID, DRONE_PAYLOAD_ID);
    }

    private void seedOrder() {
        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, user_id, title, purpose, service_id, description, address, point,
                    preferred_date, preferred_time_id, media_type, duration_of_video,
                    number_of_photo, order_status, created_at, updated_at, version
                )
                VALUES (
                    ?, CAST(? AS uuid), 'Forest Fire Monitoring - Compact Map',
                    'Realtime forest fire monitoring and thermal hotspot inspection',
                    ?, 'Inspect the forest fire zone and capture thermal evidence for response planning.',
                    'Forest Monitoring Compact Simulation Area',
                    ST_SetSRID(ST_MakePoint(-122.378, 37.7983), 4326),
                    ?, (SELECT id FROM preferred_times WHERE code = 'MORNING'), 'VIDEO', 45, 24, 'IN_PROGRESS',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title,
                    purpose = EXCLUDED.purpose,
                    service_id = EXCLUDED.service_id,
                    description = EXCLUDED.description,
                    address = EXCLUDED.address,
                    point = EXCLUDED.point,
                    preferred_date = EXCLUDED.preferred_date,
                    preferred_time_id = EXCLUDED.preferred_time_id,
                    media_type = EXCLUDED.media_type,
                    duration_of_video = EXCLUDED.duration_of_video,
                    number_of_photo = EXCLUDED.number_of_photo,
                    order_status = EXCLUDED.order_status,
                    updated_at = CURRENT_TIMESTAMP
                """, ORDER_ID, CUSTOMER_ID, SERVICE_ID, Date.valueOf("2026-09-19"));
    }

    private void seedMission() {
        jdbcTemplate.update("""
                INSERT INTO missions (
                    id, mission_code, status, order_id, scheduled_start_at, started_at,
                    description, preflight_retry_count, preflight_passed, preflight_checked_at,
                    created_at, updated_at, version
                )
                VALUES (
                    ?, ?, 'IN_FLIGHT', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    'Fly the compact forest map route, scan the fire area, and verify thermal hotspots.',
                    1, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                ON CONFLICT (id) DO UPDATE SET
                    mission_code = EXCLUDED.mission_code,
                    status = EXCLUDED.status,
                    order_id = EXCLUDED.order_id,
                    scheduled_start_at = EXCLUDED.scheduled_start_at,
                    started_at = EXCLUDED.started_at,
                    description = EXCLUDED.description,
                    preflight_retry_count = EXCLUDED.preflight_retry_count,
                    preflight_passed = EXCLUDED.preflight_passed,
                    preflight_checked_at = EXCLUDED.preflight_checked_at,
                    updated_at = CURRENT_TIMESTAMP
                """, MISSION_ID, MISSION_ID, ORDER_ID);
    }

    private void seedAssignments() {
        jdbcTemplate.update("""
                INSERT INTO mission_drone_assignments (
                    id, mission_id, drone_id, assigned_by, assignment_source, status,
                    is_current, assigned_at, created_at, updated_at, version
                )
                VALUES ('MDA-2024-0891-A', ?, (SELECT id FROM drones WHERE drone_code = 'DRN-0047'), 'SYSTEM_SEED', 'DEMO_SEED', 'ACTIVE',
                    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (id) DO UPDATE SET
                    mission_id = EXCLUDED.mission_id,
                    drone_id = EXCLUDED.drone_id,
                    assigned_by = EXCLUDED.assigned_by,
                    assignment_source = EXCLUDED.assignment_source,
                    status = EXCLUDED.status,
                    is_current = EXCLUDED.is_current,
                    updated_at = CURRENT_TIMESTAMP
                """, MISSION_ID);

        jdbcTemplate.update("""
                INSERT INTO mission_operator_assignments (
                    id, mission_id, operator_id, assigned_by, status, is_current,
                    assigned_at, responded_at, created_at, updated_at, version
                )
                VALUES ('MOA-2024-0891-A', ?, ?, 'SYSTEM_SEED', 'ACCEPTED', true,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (id) DO UPDATE SET
                    mission_id = EXCLUDED.mission_id,
                    operator_id = EXCLUDED.operator_id,
                    assigned_by = EXCLUDED.assigned_by,
                    status = EXCLUDED.status,
                    is_current = EXCLUDED.is_current,
                    responded_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """, MISSION_ID, OPERATOR_ID);
    }

    private void seedPlan() {
        jdbcTemplate.update("""
                INSERT INTO mission_plans (
                    id, mission_id, planning_algorithm, planned_distance_m,
                    planned_duration_sec, planned_cruise_speed_mps,
                    max_planned_altitude_m, estimated_energy_mah,
                    estimated_battery_used_percent, available_battery_percent_at_planning,
                    safety_reserve_percent, required_battery_percent,
                    feasibility_status, planning_time_ms,
                    created_at, updated_at, version
                )
                VALUES (?, ?, 'ASTAR_ENERGY_AWARE', 4200, 2660, 5.5, 60, 980,
                    19.6, 100, 20, 39.6, 'FEASIBLE', 84,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (mission_id) DO UPDATE SET
                    planning_algorithm = EXCLUDED.planning_algorithm,
                    planned_distance_m = EXCLUDED.planned_distance_m,
                    planned_duration_sec = EXCLUDED.planned_duration_sec,
                    planned_cruise_speed_mps = EXCLUDED.planned_cruise_speed_mps,
                    max_planned_altitude_m = EXCLUDED.max_planned_altitude_m,
                    estimated_energy_mah = EXCLUDED.estimated_energy_mah,
                    estimated_battery_used_percent = EXCLUDED.estimated_battery_used_percent,
                    available_battery_percent_at_planning = EXCLUDED.available_battery_percent_at_planning,
                    safety_reserve_percent = EXCLUDED.safety_reserve_percent,
                    required_battery_percent = EXCLUDED.required_battery_percent,
                    feasibility_status = EXCLUDED.feasibility_status,
                    planning_time_ms = EXCLUDED.planning_time_ms,
                    updated_at = CURRENT_TIMESTAMP
                """, MISSION_PLAN_ID, MISSION_ID);
    }

    private void seedWaypoints() {
        String missionPlanId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM mission_plans
                WHERE mission_id = ?
                """, String.class, MISSION_ID);

        Object[][] waypoints = {
                {"WP-2024-0891-00", 0, 0.0, 0.0, 12.0, 4.0, "START"},
                {"WP-2024-0891-01", 1, -42.0, 76.0, 35.0, 5.5, "CRUISE"},
                {"WP-2024-0891-02", 2, -92.0, 132.0, 48.0, 5.5, "TERRAIN_CLEARANCE"},
                {"WP-2024-0891-03", 3, -142.0, 168.0, 55.0, 4.5, "TARGET_APPROACH"},
                {"WP-2024-0891-04", 4, -178.0, 190.0, 60.0, 3.5, "TARGET"},
                {"WP-2024-0891-05", 5, -54.0, 42.0, 30.0, 5.5, "RETURN"}
        };

        for (Object[] waypoint : waypoints) {
            jdbcTemplate.update("""
                    INSERT INTO plan_waypoints (
                        id, mission_plan_id, sequence, sim_x, sim_y, altitude_m,
                        planned_speed_mps, reason, created_at, updated_at, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    ON CONFLICT (mission_plan_id, sequence) DO UPDATE SET
                        sim_x = EXCLUDED.sim_x,
                        sim_y = EXCLUDED.sim_y,
                        altitude_m = EXCLUDED.altitude_m,
                        planned_speed_mps = EXCLUDED.planned_speed_mps,
                        reason = EXCLUDED.reason,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    waypoint[0],
                    missionPlanId,
                    waypoint[1],
                    waypoint[2],
                    waypoint[3],
                    waypoint[4],
                    waypoint[5],
                    waypoint[6]);
        }
    }
}
