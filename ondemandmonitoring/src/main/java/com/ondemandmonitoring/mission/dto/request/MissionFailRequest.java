package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Request body sent when a mission or step fails, carrying the failure reason. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionFailRequest {

    @NotBlank(message = "Fail reason cannot blank!")
    String reason;
}
