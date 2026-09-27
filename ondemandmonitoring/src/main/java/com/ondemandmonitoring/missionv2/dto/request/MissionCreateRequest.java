package com.ondemandmonitoring.missionv2.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class MissionCreateRequest {
    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotNull(message = "scheduledStartAt is required")
    private Instant scheduledStartAt;

    @NotNull(message = "scheduledEndAt is required")
    private Instant scheduledEndAt;
}
