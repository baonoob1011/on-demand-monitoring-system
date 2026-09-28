package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.mission.enums.StaffResponseStatus;
import com.ondemandmonitoring.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

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
    @Column(name = "status", length = 50)
    StaffResponseStatus status;

    @Column(name = "is_current", nullable = false)
    Boolean isCurrent = true;

    @Column(name = "assigned_at")
    Instant assignedAt;

    @Column(name = "responded_at")
    Instant respondedAt;

    @Column(name = "released_at")
    Instant releasedAt;

    @Column(name = "release_reason", length = 500)
    String releaseReason;

    @Column(name = "rejection_reason", length = 500)
    String rejectionReason;
}
