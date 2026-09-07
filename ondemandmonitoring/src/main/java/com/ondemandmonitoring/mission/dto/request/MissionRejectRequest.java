package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Request body sent by Drone Operator to reject a mission assignment. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionRejectRequest {

    @NotBlank(message = "Reject reason cannot blank!")
    String reason;
}

