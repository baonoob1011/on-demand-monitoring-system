package com.ondemandmonitoring.device.dto.response;

import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import java.time.Instant;
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
public class DroneResponse {

    private String id;
    private String deviceId;
    private String deviceCode;
    private String deviceName;
    private String serialNumber;
    private DroneModelResponse droneModel;
    private DronePayloadResponse dronePayload;
    private DeviceOperationalStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
