package com.ondemandmonitoring.missionv2.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.missionv2.enums.MissionV2Status;
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
        @UniqueConstraint(name = "uk_missions_order_id", columnNames = { "order_id" })
})
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionV2 extends BaseEntity {

    @Column(name = "mission_code", nullable = false, unique = true, length = 50)
    String missionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    MissionV2Status status;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    Order order;

    @Column(name = "scheduled_start_at", nullable = false)
    Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    Instant scheduledEndAt;

    @Column(name = "actual_start_at")
    Instant actualStartAt;

    @Column(name = "actual_end_at")
    Instant actualEndAt;

    @Column(name = "failure_reason", length = 1000)
    String failureReason;
}
