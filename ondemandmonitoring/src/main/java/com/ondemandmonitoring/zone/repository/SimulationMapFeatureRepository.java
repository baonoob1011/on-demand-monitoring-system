package com.ondemandmonitoring.zone.repository;

import com.ondemandmonitoring.zone.domain.SimulationMapFeature;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationMapFeatureRepository extends JpaRepository<SimulationMapFeature, String> {

    boolean existsByCode(String code);

    Optional<SimulationMapFeature> findByCode(String code);

    List<SimulationMapFeature> findAllByOrderByDisplayOrderAscCodeAsc();
}
