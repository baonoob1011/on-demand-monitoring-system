package com.ondemandmonitoring.drone.dto.response;

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
public class DronePayloadResponse {

    private String id;
    private String modelName;
    private String sensorType;
    private Double weightKg;
    private String payloadCapabilities;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
