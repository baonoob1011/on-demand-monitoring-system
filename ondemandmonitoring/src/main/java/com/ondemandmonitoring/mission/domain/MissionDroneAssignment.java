package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.missionv2.enums.DeviceRole;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "mission_drone_assignments")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionDroneAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_role", nullable = false, length = 50)
    DeviceRole deviceRole = DeviceRole.MAIN;

    @Column(name = "status", nullable = false, length = 50)
    String status; // ACTIVE / RELEASED

    @Column(name = "is_current", nullable = false)
    Boolean isCurrent = true;

    @Column(name = "release_reason", length = 200)
    String releaseReason; // BATTERY_FAIL / HARDWARE_FAIL / MISSION_COMPLETE / REPLACED

    @Column(name = "assigned_at", nullable = false)
    Instant assignedAt = Instant.now();

    @Column(name = "released_at")
    Instant releasedAt;
}
