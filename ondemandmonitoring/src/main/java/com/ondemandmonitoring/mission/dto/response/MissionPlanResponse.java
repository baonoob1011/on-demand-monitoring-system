package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionPlanResponse {

    String id;
    PlanningAlgorithm planningAlgorithm;
    Double plannedDistanceM;
    Double plannedDurationSec;
    Double plannedCruiseSpeedMps;
    Double maxPlannedAltitudeM;
    Double estimatedEnergyMah;
    Double estimatedBatteryUsedPercent;
    Double batteryCapacityMah;
    Double availableBatteryPercentAtPlanning;
    Double estimatedRemainingBatteryPercent;
    Double safetyReservePercent;
    Double requiredBatteryPercent;
    FeasibilityStatus feasibilityStatus;
    Long planningTimeMs;
    List<PlanWaypointResponse> waypoints;
}
