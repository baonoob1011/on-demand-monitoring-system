package com.ondemandmonitoring.mission.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostflightCheckResponse {
    String id;
    String missionId;
    String droneCode;
    String checkedBy;
    Boolean batteryOk;
    Boolean motorOk;
    Boolean cameraOk;
    Boolean gpsOk;
    Boolean communicationOk;
    Boolean physicalConditionOk;
    Boolean overallOk;
    String faultType;
    String notes;
    Double landingBatteryPercent;
    String landingBatteryState;
    Double landingAltitudeM;
    Double landingSpeedMps;
    Double landingHeadingDeg;
    Boolean landingTelemetryOnline;
    Instant checkedAt;
}
