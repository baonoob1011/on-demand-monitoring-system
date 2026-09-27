package com.ondemandmonitoring.missionv2.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.missionv2.enums.StaffResponseStatus;
import com.ondemandmonitoring.user.domain.User;
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
@Table(name = "mission_staff_assignments")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionStaffAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    MissionV2 mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    User staff;

    @Column(name = "assigned_role", length = 50)
    String assignedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", length = 50)
    StaffResponseStatus responseStatus;

    @Column(name = "response_at")
    Instant responseAt;

    @Column(name = "decline_reason", length = 500)
    String declineReason;

    @Column(name = "assigned_at")
    Instant assignedAt;

    @Column(name = "responded_at")
    Instant respondedAt;
}
