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
@Table(name = "gcs_sessions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GcsSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drone_id", nullable = false)
    Drone drone;

    @Column(name = "operator_id", length = 100)
    String operatorId;

    @Column(name = "connection_status", nullable = false, length = 50)
    String connectionStatus; // CONNECTED / DISCONNECTED / LOST

    @Column(name = "telemetry_active", nullable = false)
    Boolean telemetryActive = true;

    @Column(name = "connected_at", nullable = false)
    Instant connectedAt = Instant.now();

    @Column(name = "disconnected_at")
    Instant disconnectedAt;

    @Column(name = "disconnect_reason", length = 200)
    String disconnectReason; // NORMAL / SIGNAL_LOSS / EMERGENCY
}
