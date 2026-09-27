package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.missionv2.enums.MissionStaffRole;
import com.ondemandmonitoring.missionv2.enums.StaffResponseStatus;
import com.ondemandmonitoring.user.domain.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "mission_operator_assignments")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionOperatorAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    User staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", length = 50)
    MissionStaffRole assignedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    StaffResponseStatus status; // PENDING / ACCEPTED / REJECTED

    @Column(name = "rejection_reason", length = 500)
    String rejectionReason;

    @Column(name = "is_current", nullable = false)
    Boolean isCurrent = true;

    @Column(name = "assigned_at", nullable = false)
    Instant assignedAt = Instant.now();

    @Column(name = "responded_at")
    Instant respondedAt;

    @Column(name = "released_at")
    Instant releasedAt;
}
