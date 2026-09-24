package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;

public interface PlanningResearchAnalysisService {

    PlanningResearchAnalysis analyze(PlanningExperimentResult result);
}
