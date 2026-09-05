package com.ondemandmonitoring.device.dto.response;

import com.ondemandmonitoring.device.domain.PreflightCheck;
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
    Boolean overallPassed;
    String failureReason;
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
    Instant checkedAt;

    public static PreflightCheckResponse from(PreflightCheck preflightCheck) {
        return PreflightCheckResponse.builder()
                .id(preflightCheck.getId())
                .deviceCode(preflightCheck.getDevice().getDeviceCode())
                .overallPassed(preflightCheck.getOverallPassed())
                .failureReason(preflightCheck.getFailureReason())
                .batteryPercent(preflightCheck.getBatteryPercent())
                .gpsFixType(preflightCheck.getGpsFixType())
                .gpsSatelliteCount(preflightCheck.getGpsSatelliteCount())
                .gyrometerOk(preflightCheck.getGyrometerOk())
                .accelerometerOk(preflightCheck.getAccelerometerOk())
                .magnetometerOk(preflightCheck.getMagnetometerOk())
                .localPositionOk(preflightCheck.getLocalPositionOk())
                .globalPositionOk(preflightCheck.getGlobalPositionOk())
                .homePositionOk(preflightCheck.getHomePositionOk())
                .armable(preflightCheck.getArmable())
                .connected(preflightCheck.getConnected())
                .inAir(preflightCheck.getInAir())
                .flightMode(preflightCheck.getFlightMode())
                .checkedAt(preflightCheck.getCheckedAt())
                .build();
    }
}
