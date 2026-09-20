package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.mission.enums.WaypointReason;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanWaypointResponse {

    String id;
    Integer sequence;
    Double simX;
    Double simY;
    Double altitudeM;
    Double plannedSpeedMps;
    WaypointReason reason;
}
