package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.LocalDate;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;

@Getter
@Setter
@Entity
@Table(name = "mission_operator_assignments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"operator_id", "preferred_date", "preferred_time_id", "is_current"})
})
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionOperatorAssignment extends BaseEntity {

    @Column(name = "preferred_date")
    LocalDate preferredDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_time_id")
    PreferredTime preferredTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    Mission mission;

    @Column(name = "operator_id", nullable = false, length = 100)
    String operatorId;

    @Column(name = "assigned_by", length = 100)
    String assignedBy;

    @Column(name = "status", nullable = false, length = 50)
    String status; // PENDING / ACCEPTED / REJECTED

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
