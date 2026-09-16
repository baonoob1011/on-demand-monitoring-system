package com.ondemandmonitoring.controlgateway.dto;

import jakarta.validation.constraints.NotBlank;

public record CommandRequest(
        @NotBlank String commandId,
        String droneId
) {
}
