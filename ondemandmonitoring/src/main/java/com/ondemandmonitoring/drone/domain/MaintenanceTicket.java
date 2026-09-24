package com.ondemandmonitoring.drone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "maintenance_tickets")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaintenanceTicket extends BaseEntity {

    @Column(name = "ticket_code", nullable = false, unique = true, length = 100)
    String ticketCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drone_id", nullable = false)
    Drone drone;

    @Column(name = "reported_by", length = 100)
    String reportedBy;

    @Column(name = "issue_type", nullable = false, length = 100)
    String issueType; // PREFLIGHT_HARDWARE_FAIL / POSTFLIGHT_DAMAGE / ROUTINE

    @Column(name = "severity", nullable = false, length = 50)
    String severity; // LOW / MEDIUM / HIGH / CRITICAL

    @Column(name = "description", length = 2000)
    String description;

    @Column(name = "status", nullable = false, length = 50)
    String status = "OPEN"; // OPEN / IN_PROGRESS / RESOLVED / CLOSED

    @Column(name = "opened_at", nullable = false)
    Instant openedAt = Instant.now();

    @Column(name = "resolved_at")
    Instant resolvedAt;
}
