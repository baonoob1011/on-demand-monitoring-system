package com.ondemandmonitoring.mission.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class MissionCreateRequest {
    private String orderId;
    @Size(max = 255)
    private String replacesMissionId;

    @NotBlank
    @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private String idempotencyKey;

    @NotNull
    @PositiveOrZero
    private Long expectedPlanRevision;

    @NotBlank
    @Size(max = 255)
    private String scopeLabel;

    // Explicitly reject deferred features rather than silently ignoring supplied planning intent.
    private JsonNode targetPoint;
    private JsonNode targetArea;
    private List<String> checklistItemIds;

    @JsonAlias("scheduledStart")
    @NotNull(message = "scheduledStartAt is required")
    private Instant scheduledStartAt;

    @JsonAlias("scheduledEnd")
    @NotNull(message = "scheduledEndAt is required")
    private Instant scheduledEndAt;
}
