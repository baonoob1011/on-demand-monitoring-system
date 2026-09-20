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
    @Override
    Optional<Mission> findById(String id);

    @EntityGraph(attributePaths = "order")
    @Query("SELECT m FROM Mission m WHERE m.id = :id")
    Optional<Mission> findByIdWithOrder(@Param("id") String id);

    Optional<Mission> findByMissionCode(String missionCode);

    @Query("""
            SELECT m FROM Mission m
            JOIN MissionOperatorAssignment moa ON moa.mission = m
            WHERE moa.operatorId = :operatorId
              AND moa.isCurrent = true
              AND m.status IN :statuses
            """)
    List<Mission> findByOperatorIdAndStatusIn(@Param("operatorId") String operatorId, @Param("statuses") List<MissionStatus> statuses);

    /**
     * Find missions assigned to a drone whose scheduled window overlaps [startAt, endAt].
     * Used to validate drone replacement availability / schedule conflict.
     */
    @Query("""
            SELECT m FROM Mission m
            JOIN MissionDroneAssignment mda ON mda.mission = m
            WHERE mda.drone.id = :droneId
              AND mda.isCurrent = true
              AND m.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
              AND m.completedAt IS NULL
            """)
    List<Mission> findActiveByDroneId(@Param("droneId") String droneId);
}


