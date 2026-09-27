package com.ondemandmonitoring.mission.dto.request;

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

    String deviceId;

    String droneId;

    String operatorId;
}
