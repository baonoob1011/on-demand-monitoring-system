package com.ondemandmonitoring.warehouse.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "preferred_times")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferredTime extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 80)
    private PreferredTimeCode code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

}
