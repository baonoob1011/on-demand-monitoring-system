package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionOperatorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionOperatorAssignmentRepository extends JpaRepository<MissionOperatorAssignment, String> {

    List<MissionOperatorAssignment> findByMissionId(String missionId);

    Optional<MissionOperatorAssignment> findByMissionIdAndIsCurrentTrue(String missionId);

    Optional<MissionOperatorAssignment> findFirstByMissionIdOrderByAssignedAtDesc(String missionId);

    boolean existsByMissionIdAndOperatorId(String missionId, String operatorId);
}
