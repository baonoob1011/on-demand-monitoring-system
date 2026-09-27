package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionDeviceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionDeviceAssignmentRepository extends JpaRepository<MissionDeviceAssignment, String> {

    List<MissionDeviceAssignment> findByMissionId(String missionId);

    Optional<MissionDeviceAssignment> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);

    Optional<MissionDeviceAssignment> findFirstByMissionIdAndDeviceRoleOrderByCreatedAtDesc(String missionId,
                                                                                           com.ondemandmonitoring.mission.enums.DeviceRole deviceRole);
}
