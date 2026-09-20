package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlanningBenchmarkDefinition;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkGenerationResult;

public interface PlanningBenchmarkScenarioGenerator {

    PlanningBenchmarkGenerationResult generate(PlanningBenchmarkDefinition definition);
}
