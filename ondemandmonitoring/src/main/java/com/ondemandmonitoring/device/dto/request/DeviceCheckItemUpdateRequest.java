package com.ondemandmonitoring.device.dto.request;

import com.ondemandmonitoring.device.enums.DeviceCheckItemStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceCheckItemUpdateRequest {

    @NotNull
    DeviceCheckItemStatus status;

    String message;
}

