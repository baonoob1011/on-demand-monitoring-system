package com.ondemandmonitoring.drone.domain;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * In-memory snapshot of Preflight Diagnostic Checklist results.
 * Note: Per the final DB schema design, preflight checks run in-memory
 * and results are saved inline on the Mission table (no preflight_checks table).
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreflightCheck {

    String id;
    Drone drone;

    /** Mission this pre-flight check was performed for. Nullable for legacy stand-alone checks. */
    String missionId;

    Double batteryPercent;
    Double latitude;
    Double longitude;
    Double absoluteAltitude;
    Double relativeAltitude;
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
    Boolean armed;
    String flightMode;
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

    // ===== Extended checklist =====

    Boolean cameraOk;
    Boolean gimbalOk;
    Long storageAvailableMb;
    Boolean storageOk;
    Boolean weatherOk;
    String weatherNotes;

    String faultType;
    Boolean overallPassed;
    String failureReason;
    Instant checkedAt;
}
