package com.ondemandmonitoring.mission.dto.request;

import com.ondemandmonitoring.mission.enums.DeviceRole;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignDeviceRequest {
    @NotBlank(message = "deviceId is required")
    private String deviceId;

    private DeviceRole deviceRole;
}
