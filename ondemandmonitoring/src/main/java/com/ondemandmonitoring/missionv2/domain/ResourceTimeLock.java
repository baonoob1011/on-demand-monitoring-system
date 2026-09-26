package com.ondemandmonitoring.missionv2.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.missionv2.enums.LockStatus;
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
@Table(name = "resource_time_locks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_time_locks_resource_mission", columnNames = { "resource_id",
                "mission_id" })
})
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResourceTimeLock extends BaseEntity {

    @Column(name = "resource_id", nullable = false, length = 100)
    String resourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    MissionV2 mission;

    @Column(name = "start_time", nullable = false)
    Instant startTime;

    @Column(name = "end_time", nullable = false)
    Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_status", nullable = false, length = 50)
    LockStatus lockStatus;

    @Column(name = "expires_at")
    Instant expiresAt;
}
