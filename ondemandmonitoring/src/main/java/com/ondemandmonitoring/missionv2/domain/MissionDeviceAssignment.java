package com.ondemandmonitoring.missionv2.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.missionv2.enums.CheckupStatus;
import com.ondemandmonitoring.missionv2.enums.DeviceRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mission_device_assignments")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionDeviceAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    MissionV2 mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    Device device;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "device_role", nullable = false, length = 50)
    DeviceRole deviceRole = DeviceRole.MAIN;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "checkup_status", nullable = false, length = 50)
    CheckupStatus checkupStatus = CheckupStatus.PENDING;

    @Column(name = "postcheck_status", length = 50)
    String postcheckStatus;

    @Column(name = "verified_by", length = 100)
    String verifiedBy;

    @Column(name = "verified_at")
    Instant verifiedAt;

    @Column(name = "failure_notes", columnDefinition = "TEXT")
    String failureNotes;
}
