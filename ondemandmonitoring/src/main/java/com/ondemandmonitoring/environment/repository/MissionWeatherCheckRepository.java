package com.ondemandmonitoring.environment.repository;

import com.ondemandmonitoring.environment.domain.MissionWeatherCheck;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionWeatherCheckRepository extends JpaRepository<MissionWeatherCheck, String> {
    List<MissionWeatherCheck> findByMissionIdOrderByCreatedAtDesc(String missionId);

    Optional<MissionWeatherCheck> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);
}
