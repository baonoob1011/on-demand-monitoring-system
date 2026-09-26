package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.mission.enums.MediaType;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionResponse {

    String id;
    String orderId;
    String orderTitle;
    String customerName;
    String missionCode;
    MissionStatus status;
    String operatorId;
    String droneId;
    String droneCode;
    Double latitude;
    Double longitude;
    Double radiusM;
    String address;
    Instant scheduledStartAt;
    Instant startedAt;
    Instant completedAt;
    String description;
    String failureReason;
    String rejectionReason;
    MediaType mediaType;
    MissionPlanResponse plan;

    // Preflight Inline Fields
    Integer preflightRetryCount;
    Boolean preflightPassed;
    String preflightFaultType;
    String preflightFailureReason;
    Instant preflightCheckedAt;
}

