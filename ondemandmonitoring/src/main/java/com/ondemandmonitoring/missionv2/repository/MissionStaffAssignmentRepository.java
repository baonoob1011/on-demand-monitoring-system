package com.ondemandmonitoring.missionv2.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ondemandmonitoring.missionv2.domain.MissionStaffAssignment;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionStaffAssignmentRepository extends JpaRepository<MissionStaffAssignment, String> {

    Optional<MissionStaffAssignment> findByMissionIdAndStaffId(String missionId, String staffId);

    List<MissionStaffAssignment> findByMissionId(String missionId);

    List<MissionStaffAssignment> findByStaffId(String staffId);
}
