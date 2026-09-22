package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MissionDroneAssignmentRepository extends JpaRepository<MissionDroneAssignment, String> {

    List<MissionDroneAssignment> findByMissionId(String missionId);

    Optional<MissionDroneAssignment> findByMissionIdAndIsCurrentTrue(String missionId);

    @Query("""
            SELECT mda FROM MissionDroneAssignment mda
            JOIN FETCH mda.mission m
            JOIN FETCH mda.drone d
            WHERE mda.isCurrent = true
              AND d.droneCode = :droneCode
              AND m.status IN :statuses
            """)
    List<MissionDroneAssignment> findCurrentByDroneCodeAndMissionStatusIn(
            @Param("droneCode") String droneCode,
            @Param("statuses") List<MissionStatus> statuses);
}
