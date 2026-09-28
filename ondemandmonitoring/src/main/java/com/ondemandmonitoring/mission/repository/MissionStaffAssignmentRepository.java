package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionStaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionStaffAssignmentRepository extends JpaRepository<MissionStaffAssignment, String> {

    Optional<MissionStaffAssignment> findByMissionIdAndStaffId(String missionId, String staffId);

    Optional<MissionStaffAssignment> findFirstByMissionIdOrderByAssignedAtDesc(String missionId);

    List<MissionStaffAssignment> findByMissionId(String missionId);

    List<MissionStaffAssignment> findByStaffId(String staffId);
}
