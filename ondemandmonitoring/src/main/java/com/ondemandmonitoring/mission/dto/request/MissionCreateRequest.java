package com.ondemandmonitoring.mission.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonAlias("scheduledStart")
    @NotNull(message = "scheduledStartAt is required")
    private Instant scheduledStartAt;

    @JsonAlias("scheduledEnd")
    @NotNull(message = "scheduledEndAt is required")
    private Instant scheduledEndAt;
}
