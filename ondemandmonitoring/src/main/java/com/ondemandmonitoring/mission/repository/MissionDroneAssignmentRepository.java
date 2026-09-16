package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionDroneAssignmentRepository extends JpaRepository<MissionDroneAssignment, String> {

    List<MissionDroneAssignment> findByMissionId(String missionId);

    Optional<MissionDroneAssignment> findByMissionIdAndIsCurrentTrue(String missionId);
}
