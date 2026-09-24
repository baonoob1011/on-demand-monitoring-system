package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.PersistedPreflightCheck;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersistedPreflightCheckRepository extends JpaRepository<PersistedPreflightCheck, String> {

    List<PersistedPreflightCheck> findByMissionIdOrderByCreatedAtDesc(String missionId);

    Optional<PersistedPreflightCheck> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct run from PersistedPreflightCheck run "
            + "left join fetch run.items "
            + "where run.id = :id")
    Optional<PersistedPreflightCheck> findByIdForUpdate(@Param("id") String id);
}
