package com.ondemandmonitoring.device.dto.response;

import com.ondemandmonitoring.device.domain.PreflightCheck;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreflightCheckResponse {

    String id;
    String deviceCode;
    String missionId;
    Boolean overallPassed;
    String failureReason;
    String faultType;
    Double batteryPercent;
    String gpsFixType;
    Integer gpsSatelliteCount;
    Boolean gyrometerOk;
    Boolean accelerometerOk;
    Boolean magnetometerOk;
    Boolean localPositionOk;
    Boolean globalPositionOk;
    Boolean homePositionOk;
    Boolean armable;
    Boolean connected;
    Boolean inAir;
    String flightMode;

    // Extended checklist fields (per diagram)
    Boolean cameraOk;
    Boolean gimbalOk;
    Long storageAvailableMb;
    Boolean storageOk;
    Boolean weatherOk;
    String weatherNotes;

    // Flight Access Token issued when preflight passes
    FlightTokenResponse flightToken;

    Instant checkedAt;
}


