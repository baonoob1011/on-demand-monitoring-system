package com.ondemandmonitoring.drone.dto.request;

import com.ondemandmonitoring.drone.enums.DroneStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class DroneCreateRequest {

    @NotBlank(message = "Serial number is required")
    private String serialNumber;

    @NotBlank(message = "Model ID is required")
    private String droneModelId;

    private String dronePayloadId;

    @NotNull(message = "Status is required")
    private DroneStatus status;
}
