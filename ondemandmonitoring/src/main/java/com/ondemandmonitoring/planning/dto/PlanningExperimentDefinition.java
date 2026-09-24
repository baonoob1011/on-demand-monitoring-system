package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record PlanningExperimentDefinition(
        String experimentId,
        String name,
        double homeX,
        double homeY,
        List<PlanningExperimentScenario> scenarios) {

    public PlanningExperimentDefinition {
        scenarios = scenarios == null ? null : List.copyOf(scenarios);
    }
}
