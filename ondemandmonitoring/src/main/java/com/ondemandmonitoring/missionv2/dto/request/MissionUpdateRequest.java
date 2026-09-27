package com.ondemandmonitoring.missionv2.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class MissionUpdateRequest {
    private Instant scheduledStartAt;
    private Instant scheduledEndAt;
    private String rescheduleReason;
}
