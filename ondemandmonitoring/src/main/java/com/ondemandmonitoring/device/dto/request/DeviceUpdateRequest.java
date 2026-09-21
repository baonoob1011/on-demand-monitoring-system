package com.ondemandmonitoring.device.dto.request;

import com.ondemandmonitoring.device.enums.DeviceStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUpdateRequest {

    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private String modelId;

    private DeviceStatus status;
}
