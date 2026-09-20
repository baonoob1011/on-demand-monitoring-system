package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.EnergyEstimateRequest;

public interface MissionEnergyEstimator {

    EnergyEstimate estimate(EnergyEstimateRequest request);
}
