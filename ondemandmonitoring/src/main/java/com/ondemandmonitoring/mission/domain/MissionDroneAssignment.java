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
@Table(name = "mission_drone_assignments")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionDroneAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drone_id", nullable = false)
    Drone drone;

    @Column(name = "assigned_by", length = 100)
    String assignedBy;

    @Column(name = "assignment_source", length = 50)
    String assignmentSource; // AUTO_SYSTEM / MANUAL_MANAGER

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
