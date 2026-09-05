package com.ondemandmonitoring.device.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@Table(name = "device_telemetries")
public class DeviceTelemetry extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private Device device;

    private Double latitude;

    private Double longitude;

    private Double altitude;

    @Column(name = "battery_percent")
    private Double batteryPercent;

    private Double speed;

    @Column(name = "flight_mode")
    private String flightMode;

    private Boolean armed;
}