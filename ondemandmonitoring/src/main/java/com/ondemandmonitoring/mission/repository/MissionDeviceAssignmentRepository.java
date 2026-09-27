package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionDeviceAssignment;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionDeviceAssignmentRepository extends JpaRepository<MissionDeviceAssignment, String> {

    List<MissionDeviceAssignment> findByMissionId(String missionId);

    Optional<MissionDeviceAssignment> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);

    Optional<MissionDeviceAssignment> findFirstByMissionIdAndDeviceRoleOrderByCreatedAtDesc(String missionId,
                                                                                           com.ondemandmonitoring.mission.enums.DeviceRole deviceRole);

    @Query("""
            SELECT mda FROM MissionDeviceAssignment mda
            JOIN FETCH mda.mission m
            JOIN FETCH mda.device d
            WHERE d.serialNumber = :deviceCode
              AND m.status IN :statuses
            """)
    List<MissionDeviceAssignment> findCurrentByDeviceCodeAndMissionStatusIn(
            @Param("deviceCode") String deviceCode,
            @Param("statuses") List<MissionStatus> statuses);
}
