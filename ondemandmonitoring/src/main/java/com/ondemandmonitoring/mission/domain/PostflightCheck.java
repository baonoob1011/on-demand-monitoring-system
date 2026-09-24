package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.drone.domain.Drone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "postflight_checks")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostflightCheck extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drone_id", nullable = false)
    Drone drone;

    @Column(name = "checked_by", length = 100)
    String checkedBy;

    @Column(name = "battery_ok")
    Boolean batteryOk = true;

    @Column(name = "motor_ok")
    Boolean motorOk = true;

    @Column(name = "camera_ok")
    Boolean cameraOk = true;

    @Column(name = "gps_ok")
    Boolean gpsOk = true;

    @Column(name = "communication_ok")
    Boolean communicationOk = true;

    @Column(name = "physical_condition_ok")
    Boolean physicalConditionOk = true;

    @Column(name = "overall_ok", nullable = false)
    Boolean overallOk = true;

    @Column(name = "fault_type", length = 50)
    String faultType; // BATTERY / HARDWARE / PHYSICAL_DAMAGE

    @Column(name = "notes", length = 1000)
    String notes;

    @Column(name = "landing_battery_percent")
    Double landingBatteryPercent;

    @Column(name = "landing_battery_state", length = 30)
    String landingBatteryState;

    @Column(name = "landing_altitude_m")
    Double landingAltitudeM;

    @Column(name = "landing_speed_mps")
    Double landingSpeedMps;

    @Column(name = "landing_heading_deg")
    Double landingHeadingDeg;

    @Column(name = "landing_telemetry_online")
    Boolean landingTelemetryOnline;

    @Column(name = "checked_at", nullable = false)
    Instant checkedAt = Instant.now();
}
