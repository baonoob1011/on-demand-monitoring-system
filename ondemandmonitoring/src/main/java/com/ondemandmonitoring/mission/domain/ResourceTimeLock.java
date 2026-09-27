package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.mission.enums.LockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "resource_time_locks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_time_locks_resource_mission", columnNames = {
                "resource_id", "mission_id" })
})
public class ResourceTimeLock extends BaseEntity {

    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_status", nullable = false, length = 50)
    private LockStatus lockStatus;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
