package com.ondemandmonitoring.drone.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
public class DronePayloadCreateRequest {

    @NotBlank(message = "Model name is required")
    private String modelName;

    @NotBlank(message = "Sensor type is required")
    private String sensorType;

    @Positive(message = "Weight in kg must be positive")
    private Double weightKg;

    private String payloadCapabilities;
}
