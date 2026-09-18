package com.ondemandmonitoring.drone.dto.response;

import com.ondemandmonitoring.drone.enums.DroneStatus;
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
    private String serialNumber;
    private DroneModelResponse droneModel;
    private DronePayloadResponse dronePayload;
    private DroneStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
