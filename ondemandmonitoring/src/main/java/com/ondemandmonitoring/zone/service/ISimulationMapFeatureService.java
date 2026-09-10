package com.ondemandmonitoring.zone.service;

import com.ondemandmonitoring.zone.dto.response.SimulationMapFeatureResponse;
import java.util.List;

public interface ISimulationMapFeatureService {

    List<SimulationMapFeatureResponse> findAll();
}
