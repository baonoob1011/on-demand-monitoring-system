package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, String> {

    @EntityGraph(attributePaths = {"drone"})
    @Override
    Optional<Mission> findById(String id);

    @EntityGraph(attributePaths = {"drone"})
    Optional<Mission> findByMissionCode(String missionCode);

    @EntityGraph(attributePaths = {"drone"})
    List<Mission> findByOperatorIdAndStatusIn(String operatorId, List<MissionStatus> statuses);

    /**
     * Find missions assigned to a drone whose scheduled window overlaps [startAt, endAt].
     * Used to validate drone replacement availability / schedule conflict.
     */
    @Query("""
            SELECT m FROM Mission m
            WHERE m.drone.id = :droneId
              AND m.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
              AND m.completedAt IS NULL
            """)
    List<Mission> findActiveByDroneId(@Param("droneId") String droneId);
}


