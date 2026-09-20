package com.ondemandmonitoring.planning.dto;

import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;

public record AlgorithmDescriptiveStatistics(
        PlanningAlgorithm algorithm,
        int totalScenarioCount,
        int feasibleScenarioCount,
        int infeasibleScenarioCount,
        double feasibilityRatePercent,
        NumericDescriptiveStatistics distanceM,
        NumericDescriptiveStatistics durationSec,
        NumericDescriptiveStatistics energyMah,
        NumericDescriptiveStatistics batteryUsedPercent,
        NumericDescriptiveStatistics planningTimeMs,
        String planningTimePopulation) {
}
