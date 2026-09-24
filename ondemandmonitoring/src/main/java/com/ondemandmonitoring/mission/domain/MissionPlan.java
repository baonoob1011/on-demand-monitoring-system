package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Entity
@Table(
        name = "mission_plans",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mission_plans_mission",
                columnNames = "mission_id"))
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionPlan extends BaseEntity {

    /*
     * Mission mà kế hoạch này thuộc về.
     *
     * Một Mission có tối đa một MissionPlan hiện tại.
     *
     * Ví dụ:
     * Mission MIS-001
     *      ↓
     * MissionPlan
     *
     * Mission chứa yêu cầu cần thực hiện.
     * MissionPlan chứa kết quả tính toán trước khi drone bay.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mission_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_mission_plans_mission"))
    Mission mission;

    /*
     * Thuật toán đã được sử dụng để tạo kế hoạch.
     *
     * Ví dụ:
     * DIRECT
     * ASTAR_SHORTEST
     * ASTAR_ENERGY_AWARE
     *
     * Dùng để biết route hiện tại được tạo bởi planner nào
     * và phục vụ so sánh các thuật toán trong nghiên cứu.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "planning_algorithm", nullable = false, length = 50)
    PlanningAlgorithm planningAlgorithm;

    /*
     * Tổng quãng đường DỰ KIẾN của mission, đơn vị mét.
     *
     * Được tính từ các waypoint của planned route.
     *
     * Ví dụ:
     * HOME -> WP1 -> WP2 -> TARGET -> RETURN
     * tổng = 650.5 m
     */
    @Column(name = "planned_distance_m")
    Double plannedDistanceM;

    /*
     * Tổng thời gian bay DỰ KIẾN, đơn vị giây.
     *
     * Có thể bao gồm:
     * - climb
     * - cruise
     * - monitoring/hover
     * - return
     * - descent
     *
     * Ví dụ: 185.5 giây.
     */
    @Column(name = "planned_duration_sec")
    Double plannedDurationSec;

    /*
     * Tốc độ cruise dự kiến của drone, đơn vị m/s.
     *
     * Planner/energy estimator dùng giá trị này để
     * ước lượng thời gian và năng lượng khi bay ngang.
     *
     * Ví dụ: 5.0 m/s.
     */
    @Column(name = "planned_cruise_speed_mps")
    Double plannedCruiseSpeedMps;

    /*
     * Độ cao lớn nhất mà planned route yêu cầu, đơn vị mét.
     *
     * Với DIRECT planning trong simulation hiện tại, đây là Gazebo World Z,
     * không phải PX4 relative altitude, altitude above home, terrain-relative
     * altitude, hay NED Down.
     *
     * Giá trị này có thể phụ thuộc vào:
     * - terrain
     * - obstacle
     * - safety clearance
     *
     * Ví dụ:
     * terrain cao 40m
     * safety clearance 10m
     * => route có thể cần altitude >= 50m.
     */
    @Column(name = "max_planned_altitude_m")
    Double maxPlannedAltitudeM;

    /*
     * Năng lượng/dung lượng pin DỰ KIẾN mission sẽ tiêu thụ,
     * đơn vị mAh.
     *
     * Giá trị này sẽ do MissionEnergyEstimator tính,
     * KHÔNG tính trực tiếp trong Entity.
     *
     * Ví dụ: 720 mAh.
     */
    @Column(name = "estimated_energy_mah")
    Double estimatedEnergyMah;

    /*
     * Phần trăm pin DỰ KIẾN mission sẽ tiêu thụ.
     *
     * Ví dụ:
     * battery capacity = 5000 mAh
     * estimatedEnergy = 750 mAh
     *
     * => estimatedBatteryUsedPercent = 15%
     */
    @Column(name = "estimated_battery_used_percent")
    Double estimatedBatteryUsedPercent;

    /*
     * Battery capacity snapshot used for the planning calculation, đơn vị mAh.
     *
     * estimatedBatteryUsedPercent = estimatedEnergyMah / batteryCapacityMah * 100.
     */
    @Column(name = "battery_capacity_mah")
    Double batteryCapacityMah;

    /*
     * Phần trăm pin thực tế đang có tại thời điểm tạo plan.
     *
     * Đây là snapshot để planner đánh giá mission có đủ pin
     * để thực hiện hay không.
     *
     * Ví dụ:
     * drone đang có 72% pin
     * => availableBatteryPercentAtPlanning = 72
     */
    @Column(name = "available_battery_percent_at_planning")
    Double availableBatteryPercentAtPlanning;

    /*
     * Phần pin giữ lại làm dự phòng an toàn.
     *
     * Planner không nên lên kế hoạch sử dụng hết 100% pin.
     *
     * Ví dụ:
     * safetyReservePercent = 15%
     */
    @Column(name = "safety_reserve_percent")
    Double safetyReservePercent;

    /*
     * Tổng phần trăm pin mà mission cần có để được xem là khả thi.
     *
     * Công thức dự kiến:
     *
     * requiredBatteryPercent
     *     = estimatedBatteryUsedPercent
     *     + safetyReservePercent
     *
     * Ví dụ:
     * estimated use = 20%
     * reserve       = 15%
     * required      = 35%
     *
     * Việc tính toán nằm ở planning service,
     * Entity chỉ lưu kết quả.
     */
    @Column(name = "required_battery_percent")
    Double requiredBatteryPercent;

    /*
     * Kết quả đánh giá mission có khả thi hay không.
     *
     * Ví dụ:
     *
     * FEASIBLE
     * - có route an toàn
     * - đủ pin
     *
     * INSUFFICIENT_BATTERY
     * - tìm được route nhưng không đủ pin
     *
     * NO_SAFE_ROUTE
     * - không tìm được đường bay an toàn
     *
     * INVALID_TARGET
     * - target không hợp lệ
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "feasibility_status", nullable = false, length = 50)
    FeasibilityStatus feasibilityStatus;

    /*
     * Thời gian planner mất để tính MissionPlan,
     * đơn vị milliseconds.
     *
     * Ví dụ:
     * planningTimeMs = 83
     *
     * Field này đặc biệt hữu ích cho NCKH khi so sánh:
     * DIRECT
     * vs ASTAR_SHORTEST
     * vs ASTAR_ENERGY_AWARE
     */
    @Column(name = "planning_time_ms")
    Long planningTimeMs;

    @Column(name = "plan_version")
    Integer planVersion;

    @Column(name = "replanning_reason", length = 50)
    String replanningReason;

    @Column(name = "replanning_status", length = 50)
    String replanningStatus;

    @Column(name = "replanned_at")
    Instant replannedAt;

    /*
     * Danh sách các điểm bay tạo thành planned route.
     *
     * Ví dụ:
     *
     * MissionPlan
     *    |
     *    +-- WP0 START
     *    +-- WP1 CRUISE
     *    +-- WP2 TERRAIN_CLEARANCE
     *    +-- WP3 TARGET
     *    +-- WP4 RETURN
     *
     * Thứ tự bay được xác định bằng PlanWaypoint.sequence.
     *
     * cascade = ALL:
     * waypoint có lifecycle đi theo MissionPlan.
     *
     * orphanRemoval = true:
     * waypoint bị remove khỏi collection có thể được xóa khỏi DB.
     *
     * Quan trọng:
     * cascade này chỉ đi MissionPlan -> PlanWaypoint,
     * KHÔNG cascade ngược về Mission.
     */
    @OneToMany(
            mappedBy = "missionPlan",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    List<PlanWaypoint> waypoints = new ArrayList<>();
}
