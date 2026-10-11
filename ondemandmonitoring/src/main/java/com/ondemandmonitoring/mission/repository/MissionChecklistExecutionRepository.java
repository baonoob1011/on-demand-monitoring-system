package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionChecklistExecution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionChecklistExecutionRepository extends JpaRepository<MissionChecklistExecution, String> {
    @Query("select e from MissionChecklistExecution e join fetch e.orderChecklistItem join fetch e.missionChecklistAssignment a where e.missionId = :missionId order by a.displayOrder, a.id")
    List<MissionChecklistExecution> findOrderedByMissionId(@Param("missionId") String missionId);
}
