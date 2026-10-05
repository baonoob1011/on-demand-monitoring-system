package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionChecklistExecution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionChecklistExecutionRepository extends JpaRepository<MissionChecklistExecution, String> {
    @Query("select e from MissionChecklistExecution e join fetch e.orderChecklistItem i where e.missionId = :missionId order by i.displayOrder, i.id")
    List<MissionChecklistExecution> findOrderedByMissionId(@Param("missionId") String missionId);
}
