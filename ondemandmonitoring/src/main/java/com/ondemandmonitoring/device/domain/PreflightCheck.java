package com.ondemandmonitoring.device.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@Table(name = "preflight_checks")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreflightCheck extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    Device device;

    @Column(name = "battery_percent")
    Double batteryPercent;

    @Column(name = "latitude")
    Double latitude;

    @Column(name = "longitude")
    Double longitude;

    @Column(name = "absolute_altitude")
    Double absoluteAltitude;

    @Column(name = "relative_altitude")
    Double relativeAltitude;

    @Column(name = "gps_fix_type", length = 50)
    String gpsFixType;

    @Column(name = "gps_satellite_count")
    Integer gpsSatelliteCount;

    @Column(name = "gyrometer_ok")
    Boolean gyrometerOk;

    @Column(name = "accelerometer_ok")
    Boolean accelerometerOk;

    @Column(name = "magnetometer_ok")
    Boolean magnetometerOk;

    @Column(name = "local_position_ok")
    Boolean localPositionOk;

    @Column(name = "global_position_ok")
    Boolean globalPositionOk;

    @Column(name = "home_position_ok")
    Boolean homePositionOk;

    @Column(name = "armable")
    Boolean armable;

    @Column(name = "heading_degree")
    Double headingDegree;

    @Column(name = "velocity_north")
    Double velocityNorth;

    @Column(name = "velocity_east")
    Double velocityEast;

    @Column(name = "velocity_down")
    Double velocityDown;

    @Column(name = "ground_speed")
    Double groundSpeed;

    @Column(name = "armed")
    Boolean armed;

    @Column(name = "flight_mode", length = 50)
    String flightMode;

    @Column(name = "home_latitude")
    Double homeLatitude;

    @Column(name = "home_longitude")
    Double homeLongitude;

    @Column(name = "home_absolute_altitude")
    Double homeAbsoluteAltitude;

    @Column(name = "home_relative_altitude")
    Double homeRelativeAltitude;

    @Column(name = "roll_degree")
    Double rollDegree;

    @Column(name = "pitch_degree")
    Double pitchDegree;

    @Column(name = "yaw_degree")
    Double yawDegree;

    @Column(name = "connected")
    Boolean connected;

    @Column(name = "in_air")
    Boolean inAir;

    @Column(name = "geofence_configured")
    Boolean geofenceConfigured;

    @Column(name = "geofence_passed")
    Boolean geofencePassed;

    @Column(name = "overall_passed", nullable = false)
    Boolean overallPassed;

    @Column(name = "failure_reason", length = 2000)
    String failureReason;

    @Column(name = "checked_at", nullable = false)
    Instant checkedAt;
}
