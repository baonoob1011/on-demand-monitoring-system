package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.ResourceTimeLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceTimeLockRepository extends JpaRepository<ResourceTimeLock, String> {

    List<ResourceTimeLock> findByResourceId(String resourceId);

    Optional<ResourceTimeLock> findByResourceIdAndMissionId(String resourceId, String missionId);
}
