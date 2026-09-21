package com.ondemandmonitoring.planning.dto;

import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;

public record PlanningExperimentDatasetRow(
        String experimentId,
        String scenarioId,
        double homeX,
        double homeY,
        double targetX,
        double targetY,
        PlanningAlgorithm algorithm,
        FeasibilityStatus feasibilityStatus,
        Double plannedDistanceM,
        Double maxPlannedAltitudeM,
        Double plannedDurationSec,
        Double estimatedEnergyMah,
        Double estimatedBatteryUsedPercent,
        Double requiredBatteryPercent,
        Long planningTimeMs,
        Integer waypointCount,
        String failureReason) {
}
