package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.mission.enums.CheckupStatus;
import com.ondemandmonitoring.mission.enums.DeviceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mission_device_assignments")
public class MissionDeviceAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "device_role", nullable = false, length = 50)
    private DeviceRole deviceRole = DeviceRole.MAIN;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "checkup_status", nullable = false, length = 50)
    private CheckupStatus checkupStatus = CheckupStatus.PENDING;

    @Column(name = "postcheck_status", length = 50)
    private String postcheckStatus;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "failure_notes", columnDefinition = "TEXT")
    private String failureNotes;
}
