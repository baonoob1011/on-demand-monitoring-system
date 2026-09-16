package com.ondemandmonitoring.drone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "drone_payloads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DronePayload extends BaseEntity {

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "sensor_type", nullable = false, length = 100)
    private String sensorType;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "payload_capabilities", columnDefinition = "TEXT")
    private String payloadCapabilities;
}
