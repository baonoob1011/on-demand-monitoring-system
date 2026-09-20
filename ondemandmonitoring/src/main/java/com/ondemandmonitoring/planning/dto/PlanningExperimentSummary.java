package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record PlanningExperimentSummary(
        List<AlgorithmExperimentSummary> algorithms,
        int comparisonScenarioCount,
        Double meanEnergySavingMah,
        Double meanEnergySavingPercent,
        Double meanDistanceDifferenceM,
        Double meanDistanceDifferencePercent,
        Double meanDurationDifferenceSec,
        Double meanAltitudeDifferenceM,
        Double meanPlanningTimeDifferenceMs,
        int energySavingScenarioCount,
        int equalEnergyScenarioCount,
        int longerButLowerEnergyCount,
        int lowerAltitudeAndLowerEnergyCount) {

    public PlanningExperimentSummary {
        algorithms = List.copyOf(algorithms);
    }
}
