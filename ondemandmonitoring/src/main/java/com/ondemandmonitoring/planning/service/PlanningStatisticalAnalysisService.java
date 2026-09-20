package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningStatisticalAnalysis;

public interface PlanningStatisticalAnalysisService {

    PlanningStatisticalAnalysis analyze(
            PlanningExperimentResult experimentResult,
            PlanningResearchAnalysis researchAnalysis);
}
