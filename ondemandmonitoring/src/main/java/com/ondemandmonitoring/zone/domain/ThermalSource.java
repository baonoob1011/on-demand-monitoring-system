package com.ondemandmonitoring.zone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.zone.enums.ThermalType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "thermal_sources",
        uniqueConstraints = @UniqueConstraint(name = "uk_thermal_sources_code", columnNames = "code"))
public class ThermalSource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "zone_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_thermal_sources_zone"))
    private Zone zone;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "thermal_type", nullable = false, length = 30)
    private ThermalType thermalType;

    @Column(name = "temperature_c", nullable = false)
    private Double temperatureC;

    @Column(name = "center_x_m", nullable = false)
    private Double centerXM;

    @Column(name = "center_y_m", nullable = false)
    private Double centerYM;

    @Column(name = "radius_m", nullable = false)
    private Double radiusM;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "source_world", nullable = false, length = 120)
    private String sourceWorld;

    @Column(name = "coordinate_system", nullable = false, length = 120)
    private String coordinateSystem;
}
