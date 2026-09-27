package com.ondemandmonitoring.mission.dto.request;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Request body to replace the broken drone on a mission (during pre-flight failure). */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DroneReplacementRequest {

    String newDeviceCode;

    String newDroneCode;

    public String resolvedDeviceCode() {
        if (newDeviceCode != null && !newDeviceCode.isBlank()) {
            return newDeviceCode;
        }
        return newDroneCode;
    }
}

