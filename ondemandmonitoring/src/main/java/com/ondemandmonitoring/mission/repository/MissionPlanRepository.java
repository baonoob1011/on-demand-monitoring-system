package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionPlan;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionPlanRepository extends JpaRepository<MissionPlan, String> {

    @EntityGraph(attributePaths = "waypoints")
    Optional<MissionPlan> findByMissionId(String missionId);
}
