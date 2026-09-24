package com.ondemandmonitoring.planning.dto;

import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;

public record AlgorithmPlanningResult(
        PlanningAlgorithm algorithm,
        FeasibilityStatus feasibilityStatus,
        Double plannedDistanceM,
        Double maxPlannedAltitudeM,
        Double plannedDurationSec,
        Double plannedCruiseSpeedMps,
        Double estimatedEnergyMah,
        Double estimatedBatteryUsedPercent,
        Double availableBatteryPercentAtPlanning,
        Double safetyReservePercent,
        Double requiredBatteryPercent,
        Long planningTimeMs,
        Integer waypointCount,
        String failureReason) {
}
