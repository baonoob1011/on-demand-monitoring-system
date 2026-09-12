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

    @EntityGraph(attributePaths = {"device"})
    @Override
    Optional<Mission> findById(String id);

    @EntityGraph(attributePaths = {"device"})
    Optional<Mission> findByMissionCode(String missionCode);

    @EntityGraph(attributePaths = {"device"})
    List<Mission> findByOperatorIdAndStatusIn(String operatorId, List<MissionStatus> statuses);

    /**
     * Find missions assigned to a device whose scheduled window overlaps [startAt, endAt].
     * Used to validate drone replacement availability / schedule conflict.
     */
    @Query("""
            SELECT m FROM Mission m
            WHERE m.device.id = :deviceId
              AND m.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
              AND m.scheduledStartAt < :endAt
              AND m.completedAt IS NULL
            """)
    List<Mission> findActiveByDeviceId(@Param("deviceId") String deviceId);
}


