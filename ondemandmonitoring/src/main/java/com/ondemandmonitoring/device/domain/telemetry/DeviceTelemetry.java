package com.ondemandmonitoring.device.domain.telemetry;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Entity
@Table(name = "device_telemetries")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceTelemetry extends BaseEntity {

    @Column(name = "device_code", nullable = false, length = 100)
    String deviceCode;

    @Column(nullable = false)
    Double latitude;

    @Column(nullable = false)
    Double longitude;

    @Column(nullable = false)
    Double altitude;

    @Column(name = "battery_percent")
    Double batteryPercent;

    Double speed;

    @Column(name = "flight_mode", length = 50)
    String flightMode;

    @Column(nullable = false)
    Boolean armed;
}
