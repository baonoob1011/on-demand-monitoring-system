package com.ondemandmonitoring.missionv2.dto.response;

import com.ondemandmonitoring.mission.enums.MissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Response DTO after creating a Mission V2")
public class MissionV2Response {

    @Schema(description = "Unique identifier of the mission", example = "550e8400-e29b-41d4-a716-446655440000")
    String id;

    @Schema(description = "Unique code of the mission", example = "MS-2026-0001")
    String missionCode;

    @Schema(description = "Current status of the mission", example = "PENDING")
    MissionStatus status;

    @Schema(description = "ID of the associated order", example = "123e4567-e89b-12d3-a456-426614174000")
    String orderId;

    @Schema(description = "Scheduled start timestamp")
    Instant scheduledStartAt;

    @Schema(description = "Scheduled end timestamp")
    Instant scheduledEndAt;

    @Schema(description = "Actual start timestamp")
    Instant actualStartAt;

    @Schema(description = "Actual end timestamp")
    Instant actualEndAt;

    @Schema(description = "Reason for mission failure, if any")
    String failureReason;

    @Schema(description = "Creation timestamp")
    Instant createdAt;

    @Schema(description = "Last update timestamp")
    Instant updatedAt;
}
