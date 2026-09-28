package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.mission.domain.Mission;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "mission_reschedule_history")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionRescheduleHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    Mission mission;

    @Column(name = "previous_start_time")
    Instant previousStartTime;

    @Column(name = "previous_end_time")
    Instant previousEndTime;

    @Column(name = "new_start_time")
    Instant newStartTime;

    @Column(name = "new_end_time")
    Instant newEndTime;

    @Column(name = "reschedule_reason", length = 1000)
    String rescheduleReason;

    @Column(name = "rescheduled_by", length = 100)
    String rescheduledBy;

    @Column(name = "rescheduled_at", nullable = false)
    Instant rescheduledAt = Instant.now();
}
