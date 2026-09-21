package com.ondemandmonitoring.drone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Maintenance work order for a device (Drone or any future device type).
 * <p>
 * Relationships:
 * - 1 Device (Drone) → many MaintenanceTickets  (one device can accumulate fault history)
 * - 1 User (technician) → many MaintenanceTickets (one technician can be assigned to many tickets)
 */
@Getter
@Setter
@Entity
@Table(name = "maintenance_tickets")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaintenanceTicket extends BaseEntity {

    @Column(name = "ticket_code", nullable = false, unique = true, length = 100)
    String ticketCode;

    /**
     * The physical device (drone or other) that requires maintenance.
     * Many tickets can belong to one device over its lifetime.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    Drone device; // field name is 'device' to be device-type agnostic

    /**
     * The technician/staff user assigned to resolve this ticket.
     * One user (technician) can be assigned to many tickets.
     * Nullable — ticket may be OPEN and unassigned initially.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    User assignedTechnician;

    @Column(name = "reported_by", length = 100)
    String reportedBy; // operator id or "AUTOMATED_PREFLIGHT_GATE"

    @Column(name = "issue_type", nullable = false, length = 100)
    String issueType; // PREFLIGHT_HARDWARE_FAIL / POSTFLIGHT_DAMAGE / ROUTINE

    @Column(name = "severity", nullable = false, length = 50)
    String severity; // LOW / MEDIUM / HIGH / CRITICAL

    @Column(name = "description", length = 2000)
    String description;

    @Column(name = "status", nullable = false, length = 50)
    String status = "OPEN"; // OPEN / IN_PROGRESS / RESOLVED / CLOSED

    @Column(name = "resolution_notes", length = 2000)
    String resolutionNotes; // filled when status → RESOLVED or CLOSED

    @Column(name = "opened_at", nullable = false)
    Instant openedAt = Instant.now();

    @Column(name = "resolved_at")
    Instant resolvedAt;

    @Column(name = "closed_at")
    Instant closedAt; // set when ticket is formally closed after verification
}

