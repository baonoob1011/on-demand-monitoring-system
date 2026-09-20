package com.ondemandmonitoring.environment.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "atmosphere_profiles")
public class AtmosphereProfile extends BaseEntity {

    @Column(name = "source_world", nullable = false, length = 120)
    private String sourceWorld;

    @Column(name = "base_pressure_pa", nullable = false)
    private Double basePressurePa;

    @Column(name = "base_altitude_m", nullable = false)
    private Double baseAltitudeM;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
