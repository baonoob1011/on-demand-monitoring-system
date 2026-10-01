package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Request body to replace the broken device on a mission (during pre-flight failure). */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceReplacementRequest {

    @NotBlank(message = "device replacement code cannot blank!")
    String newDeviceCode;
}

