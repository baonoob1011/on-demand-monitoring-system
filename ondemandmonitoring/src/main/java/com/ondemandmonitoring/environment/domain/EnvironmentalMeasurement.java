package com.ondemandmonitoring.environment.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.environment.enums.MeasurementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "environmental_measurements")
public class EnvironmentalMeasurement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drone_id", foreignKey = @ForeignKey(name = "fk_environmental_measurements_drone"))
    private Drone drone;

    @Column(name = "drone_code", length = 50)
    private String droneCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_type", nullable = false, length = 40)
    private MeasurementType measurementType;

    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "altitude_m")
    private Double altitudeM;

    @Column(name = "sim_x")
    private Double simX;

    @Column(name = "sim_y")
    private Double simY;

    @Column(name = "source_world", nullable = false, length = 120)
    private String sourceWorld;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;
}
