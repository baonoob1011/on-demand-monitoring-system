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
              AND m.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'FAILED_PREFLIGHT')
              AND m.completedAt IS NULL
            """)
    List<Mission> findActiveByDroneId(@Param("droneId") String droneId);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM Mission m
            JOIN MissionDroneAssignment mda ON mda.mission = m
            WHERE mda.drone.id = :droneId
              AND mda.isCurrent = true
              AND m.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'FAILED_PREFLIGHT')
              AND m.order.preferredDate = :date
              AND m.order.preferredTime.startTime < :endTime
              AND m.order.preferredTime.endTime > :startTime
              AND m.id != :excludeMissionId
            """)
    boolean isDroneLockedForTime(@Param("droneId") String droneId,
                                 @Param("date") java.time.LocalDate date,
                                 @Param("startTime") java.time.LocalTime startTime,
                                 @Param("endTime") java.time.LocalTime endTime,
                                 @Param("excludeMissionId") String excludeMissionId);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM Mission m
            JOIN MissionOperatorAssignment moa ON moa.mission = m
            WHERE moa.operatorId = :operatorId
              AND moa.isCurrent = true
              AND m.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'FAILED_PREFLIGHT')
              AND m.order.preferredDate = :date
              AND m.order.preferredTime.startTime < :endTime
              AND m.order.preferredTime.endTime > :startTime
              AND m.id != :excludeMissionId
            """)
    boolean isOperatorLockedForTime(@Param("operatorId") String operatorId,
                                    @Param("date") java.time.LocalDate date,
                                    @Param("startTime") java.time.LocalTime startTime,
                                    @Param("endTime") java.time.LocalTime endTime,
                                    @Param("excludeMissionId") String excludeMissionId);
}


