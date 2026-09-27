package com.ondemandmonitoring.missionv2.dto.request;

import com.ondemandmonitoring.missionv2.enums.DeviceRole;

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
