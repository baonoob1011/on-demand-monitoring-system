package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionResourceAssignmentRequest {

    @NotBlank
    String droneId;

    @NotBlank
    String operatorId;
}
