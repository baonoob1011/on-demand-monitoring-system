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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

@Getter
@Setter
@Entity
@Table(name = "missions", uniqueConstraints = {
        // Phase 2 expansion only: keep the explicit DB gate for fresh Hibernate schemas.
        // Remove this constraint together with the DB unique during the later runtime cutover.
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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    @Column(name = "scope_label", length = 255)
    String scopeLabel;

    @Column(name = "target_point", columnDefinition = "geometry(Point,4326)")
    Point targetPoint;

    @Column(name = "target_area", columnDefinition = "geometry(Polygon,4326)")
    Polygon targetArea;

    @jakarta.validation.constraints.PositiveOrZero
    @Column(name = "finalized_plan_revision")
    Long finalizedPlanRevision;

    @Column(name = "creation_idempotency_key", length = 36)
    String creationIdempotencyKey;

    @Column(name = "creation_request_hash", length = 64)
    String creationRequestHash;

    @Column(name = "replaces_mission_id", length = 255)
    String replacesMissionId;

    @Column(name = "cancellation_at")
    Instant cancellationAt;

    @Column(name = "cancelled_by", length = 255)
    String cancelledBy;

    @Column(name = "cancellation_reason", length = 1000)
    String cancellationReason;

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
