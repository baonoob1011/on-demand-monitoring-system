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

    @Column(name = "device_code", nullable = false, length = 50)
    private String deviceCode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private Device device;

    private Double latitude;

    private Double longitude;

    private Double altitude;

    @Column(name = "absolute_altitude")
    private Double absoluteAltitude;

    @Column(name = "relative_altitude")
    private Double relativeAltitude;

    @Column(name = "battery_percent")
    private Double batteryPercent;

    private Double speed;

    @Column(name = "gps_fix_type", length = 50)
    private String gpsFixType;

    @Column(name = "gps_satellite_count")
    private Integer gpsSatelliteCount;

    @Column(name = "gyrometer_ok")
    private Boolean gyrometerOk;

    @Column(name = "accelerometer_ok")
    private Boolean accelerometerOk;

    @Column(name = "magnetometer_ok")
    private Boolean magnetometerOk;

    @Column(name = "local_position_ok")
    private Boolean localPositionOk;

    @Column(name = "global_position_ok")
    private Boolean globalPositionOk;

    @Column(name = "home_position_ok")
    private Boolean homePositionOk;

    @Column(name = "armable")
    private Boolean armable;

    @Column(name = "heading_degree")
    private Double headingDegree;

    @Column(name = "velocity_north")
    private Double velocityNorth;

    @Column(name = "velocity_east")
    private Double velocityEast;

    @Column(name = "velocity_down")
    private Double velocityDown;

    @Column(name = "ground_speed")
    private Double groundSpeed;

    @Column(name = "flight_mode")
    private String flightMode;

    private Boolean armed;

    @Column(name = "home_latitude")
    private Double homeLatitude;

    @Column(name = "home_longitude")
    private Double homeLongitude;

    @Column(name = "home_absolute_altitude")
    private Double homeAbsoluteAltitude;

    @Column(name = "home_relative_altitude")
    private Double homeRelativeAltitude;

    @Column(name = "roll_degree")
    private Double rollDegree;

    @Column(name = "pitch_degree")
    private Double pitchDegree;

    @Column(name = "yaw_degree")
    private Double yawDegree;

    @Column(name = "connected")
    private Boolean connected;

    @Column(name = "in_air")
    private Boolean inAir;

    @Column(name = "geofence_configured")
    private Boolean geofenceConfigured;

    @Column(name = "geofence_passed")
    private Boolean geofencePassed;
}
