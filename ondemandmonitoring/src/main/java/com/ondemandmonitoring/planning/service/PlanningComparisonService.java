package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlanningComparisonResult;
import com.ondemandmonitoring.planning.dto.PlanningComparisonInput;

public interface PlanningComparisonService {

    PlanningComparisonResult compare(String missionId);

    PlanningComparisonResult compare(PlanningComparisonInput input);
}
