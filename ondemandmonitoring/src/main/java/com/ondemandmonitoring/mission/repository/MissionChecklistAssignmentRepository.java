package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionChecklistAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionChecklistAssignmentRepository extends JpaRepository<MissionChecklistAssignment, String> {
    List<MissionChecklistAssignment> findByMissionIdAndRemovedAtIsNullOrderByDisplayOrderAscIdAsc(String missionId);

    Optional<MissionChecklistAssignment> findByMissionIdAndOrderChecklistItemIdAndRemovedAtIsNull(
            String missionId, String orderChecklistItemId);
}
