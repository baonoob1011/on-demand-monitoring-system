package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.mission.enums.MediaType;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionResponse {

    @Schema(description = "Unique identifier of the mission", example = "550e8400-e29b-41d4-a716-446655440000")
    String id;

    @Schema(description = "Unique code of the mission", example = "MS-2026-0001")
    String missionCode;

    @Schema(description = "Current status of the mission", example = "PENDING")
    MissionStatus status;

    @Schema(description = "ID of the associated order", example = "123e4567-e89b-12d3-a456-426614174000")
    String orderId;

    String orderTitle;

    String serviceName;

    String customerName;

    String address;

    Double latitude;

    Double longitude;

    Double radiusM;

    String deviceId;


    String staffId;

    String operatorId;

    MissionPlanResponse plan;

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
