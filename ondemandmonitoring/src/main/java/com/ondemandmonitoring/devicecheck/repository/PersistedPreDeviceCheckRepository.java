package com.ondemandmonitoring.devicecheck.repository;

import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheck;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersistedPreDeviceCheckRepository extends JpaRepository<PersistedPreDeviceCheck, String> {

    List<PersistedPreDeviceCheck> findByMissionIdOrderByCreatedAtDesc(String missionId);

    Optional<PersistedPreDeviceCheck> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct run from PersistedPreDeviceCheck run "
            + "left join fetch run.items "
            + "where run.id = :id")
    Optional<PersistedPreDeviceCheck> findByIdForUpdate(@Param("id") String id);
}

