package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.PersistedPreflightCheck;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistedPreflightCheckRepository extends JpaRepository<PersistedPreflightCheck, String> {

    List<PersistedPreflightCheck> findByMissionIdOrderByCreatedAtDesc(String missionId);

    Optional<PersistedPreflightCheck> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);
}
