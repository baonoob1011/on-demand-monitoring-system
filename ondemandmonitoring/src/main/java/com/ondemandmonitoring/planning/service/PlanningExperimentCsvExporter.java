package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;

public interface PlanningExperimentCsvExporter {

    String export(PlanningExperimentResult result);
}
