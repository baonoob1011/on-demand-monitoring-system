package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.device.domain.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "mission_drone_assignments")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionDroneAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    Device device;

    @Column(name = "status", length = 50)
    String status;

    @Column(name = "is_current", nullable = false)
    Boolean isCurrent = true;

    @Column(name = "assigned_at")
    Instant assignedAt;

    @Column(name = "released_at")
    Instant releasedAt;

    @Column(name = "release_reason", length = 500)
    String releaseReason;
}
