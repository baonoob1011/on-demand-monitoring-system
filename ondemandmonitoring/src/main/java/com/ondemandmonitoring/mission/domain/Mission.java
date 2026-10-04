package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.order.domain.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "missions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_missions_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_mission_id_order", columnNames = {"id", "order_id"})})
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Mission extends BaseEntity {

    @Column(name = "mission_code", nullable = false, unique = true, length = 50)
    String missionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    MissionStatus status;

    // ===== Relationship to Order =====
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    Order order;

    // ===== Schedule =====

    @Column(name = "scheduled_start_at", nullable = false)
    Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    Instant scheduledEndAt;

    @Column(name = "actual_start_at")
    Instant actualStartAt;

    @Column(name = "actual_end_at")
    Instant actualEndAt;

    @Column(name = "completed_at")
    Instant completedAt;

}
