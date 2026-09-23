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
@Table(name = "control_handovers")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ControlHandover extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_connection_id")
    DeviceConnection deviceConnection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drone_id", nullable = false)
    Drone drone;

    @Column(name = "operator_id", nullable = false, length = 100)
    String operatorId;

    @Column(name = "status", nullable = false, length = 50)
    String status; // CONFIRMED / CANCELLED

    @Column(name = "acknowledgement_text", length = 500)
    String acknowledgementText;

    @Column(name = "confirmed_at", nullable = false)
    Instant confirmedAt = Instant.now();
}
