package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.mission.domain.Mission;
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
    String missionCode;
    MissionStatus status;
    String operatorId;
    String deviceId;
    String deviceCode;
    Double latitude;
    Double longitude;
    String address;
    Instant scheduledStartAt;
    Instant startedAt;
    Instant completedAt;
    String description;
    String failureReason;
    String rejectionReason;
    MediaType mediaType;
}

