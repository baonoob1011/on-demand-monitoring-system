package com.ondemandmonitoring.zone.repository;

import com.ondemandmonitoring.zone.domain.ThermalSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThermalSourceRepository extends JpaRepository<ThermalSource, String> {

    Optional<ThermalSource> findByCode(String code);

    boolean existsByCode(String code);

    List<ThermalSource> findAllByOrderByCodeAsc();

    List<ThermalSource> findByZoneCodeOrderByCodeAsc(String zoneCode);
}
