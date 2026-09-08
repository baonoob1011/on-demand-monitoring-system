package com.ondemandmonitoring.mission.dto.request;

import com.ondemandmonitoring.device.enums.DeviceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Post-flight status update submitted by Drone Operator after the mission. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostFlightStatusRequest {

    @NotNull(message = "Drone status after flight cannot be null")
    DeviceStatus newDeviceStatus;

    String notes;
}

