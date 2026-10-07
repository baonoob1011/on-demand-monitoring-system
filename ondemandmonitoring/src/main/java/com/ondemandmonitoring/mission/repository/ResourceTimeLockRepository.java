package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.ResourceTimeLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceTimeLockRepository extends JpaRepository<ResourceTimeLock, String> {

    List<ResourceTimeLock> findByResourceId(String resourceId);

    List<ResourceTimeLock> findByMissionId(String missionId);

    @org.springframework.data.jpa.repository.Query("""
            select count(l) > 0 from ResourceTimeLock l where l.resourceId=:resourceId
              and l.mission.id<>:missionId and l.releasedAt is null
              and l.startTime<:end and l.endTime>:start
            """)
    boolean existsActiveOverlap(@org.springframework.data.repository.query.Param("resourceId") String resourceId,
            @org.springframework.data.repository.query.Param("missionId") String missionId,
            @org.springframework.data.repository.query.Param("start") java.time.Instant start,
            @org.springframework.data.repository.query.Param("end") java.time.Instant end);

    Optional<ResourceTimeLock> findByResourceIdAndMissionId(String resourceId, String missionId);

    List<ResourceTimeLock> findAllByResourceIdAndMissionId(String resourceId, String missionId);
}
