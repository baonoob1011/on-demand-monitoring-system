package com.ondemandmonitoring.environment.repository;

import com.ondemandmonitoring.environment.domain.AtmosphereProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtmosphereProfileRepository extends JpaRepository<AtmosphereProfile, String> {

    Optional<AtmosphereProfile> findFirstBySourceWorldAndActiveTrueOrderByCreatedAtDesc(String sourceWorld);

    boolean existsBySourceWorldAndActiveTrue(String sourceWorld);
}
