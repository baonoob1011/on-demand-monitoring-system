package com.ondemandmonitoring.planning.dto;

import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;

public record AlgorithmExperimentSummary(
        PlanningAlgorithm algorithm,
        int totalScenarios,
        int feasibleScenarios,
        int infeasibleScenarios,
        double feasibilityRatePercent,
        Double meanDistanceM,
        Double meanDurationSec,
        Double meanEnergyMah,
        Double meanBatteryUsedPercent,
        Double meanPlanningTimeMs,
        Long minPlanningTimeMs,
        Long maxPlanningTimeMs) {
}
