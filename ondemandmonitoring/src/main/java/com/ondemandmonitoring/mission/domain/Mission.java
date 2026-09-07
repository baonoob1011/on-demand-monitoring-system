package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.mission.enums.MediaType;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "missions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Mission extends BaseEntity {

    @Column(name = "mission_code", nullable = false, unique = true, length = 50)
    String missionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    MissionStatus status;

    // === Assignments =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    Device device;

    /** ID of the Drone Operator user assigned to this mission. */
    @Column(name = "operator_id", length = 100)
    String operatorId;

    // ===== Location =====

    @Column(name = "latitude", nullable = false)
    Double latitude;

    @Column(name = "longitude", nullable = false)
    Double longitude;

    @Column(name = "address", length = 500)
    String address;

    // ===== Schedule =====

    @Column(name = "scheduled_start_at")
    Instant scheduledStartAt;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "completed_at")
    Instant completedAt;

    // ===== Mission information =====

    @Column(name = "description", length = 1000)
    String description;

    @Column(name = "failure_reason", length = 1000)
    String failureReason;

    @Column(name = "rejection_reason", length = 1000)
    String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 30)
    MediaType mediaType;

}