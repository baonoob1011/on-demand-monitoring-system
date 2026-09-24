package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactBundle;
import com.ondemandmonitoring.planning.dto.PlanningStatisticalAnalysis;

public interface PlanningResearchArtifactService {

    PlanningResearchArtifactBundle build(
            PlanningExperimentResult experimentResult,
            PlanningResearchAnalysis researchAnalysis,
            PlanningStatisticalAnalysis statisticalAnalysis);
}
