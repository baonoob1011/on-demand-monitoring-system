package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;

public interface PlanningExperimentRunner {

    PlanningExperimentResult run(PlanningExperimentDefinition definition);
}
