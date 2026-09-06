package com.ondemandmonitoring.device.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TelemetryRequest {

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    Double latitude;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    Double longitude;

    @NotNull
    Double altitude;

    Double absoluteAltitude;

    Double relativeAltitude;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    Double batteryPercent;

    @DecimalMin(value = "0.0")
    Double speed;

    String gpsFixType;

    Integer gpsSatelliteCount;

    Boolean gyrometerOk;

    Boolean accelerometerOk;

    Boolean magnetometerOk;

    Boolean localPositionOk;

    Boolean globalPositionOk;

    Boolean homePositionOk;

    Boolean armable;

    Double headingDegree;

    Double velocityNorth;

    Double velocityEast;

    Double velocityDown;

    Double groundSpeed;

    String flightMode;

    @NotNull
    Boolean armed;

    Double homeLatitude;

    Double homeLongitude;

    Double homeAbsoluteAltitude;

    Double homeRelativeAltitude;

    Double rollDegree;

    Double pitchDegree;

    Double yawDegree;

    Boolean connected;

    Boolean inAir;

    Boolean geofenceConfigured;

    Boolean geofencePassed;
}
