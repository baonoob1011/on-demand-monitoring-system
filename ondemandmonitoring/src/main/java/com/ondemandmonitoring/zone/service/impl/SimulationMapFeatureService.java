package com.ondemandmonitoring.zone.service.impl;

import com.ondemandmonitoring.zone.dto.response.SimulationMapFeatureResponse;
import com.ondemandmonitoring.zone.mapper.SimulationMapFeatureMapper;
import com.ondemandmonitoring.zone.repository.SimulationMapFeatureRepository;
import com.ondemandmonitoring.zone.service.ISimulationMapFeatureService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SimulationMapFeatureService implements ISimulationMapFeatureService {

    SimulationMapFeatureRepository simulationMapFeatureRepository;
    SimulationMapFeatureMapper simulationMapFeatureMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SimulationMapFeatureResponse> findAll() {
        return simulationMapFeatureRepository.findAllByOrderByDisplayOrderAscCodeAsc()
                .stream()
                .map(simulationMapFeatureMapper::toResponse)
                .toList();
    }
}
