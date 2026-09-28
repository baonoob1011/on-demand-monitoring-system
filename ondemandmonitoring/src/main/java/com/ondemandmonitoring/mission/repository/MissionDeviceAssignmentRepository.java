package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionDeviceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MissionDeviceAssignmentRepository extends JpaRepository<MissionDeviceAssignment, String> {

    boolean existsByMissionId(String missionId);

    Optional<MissionDeviceAssignment> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);
}
