package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Request body to replace the broken drone on a mission (during pre-flight failure). */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DroneReplacementRequest {

    @NotBlank(message = "Drone replacement code cannot blank!")
    String newDeviceCode;
}

