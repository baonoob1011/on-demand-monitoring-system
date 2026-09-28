package com.ondemandmonitoring.devicecheck.repository;

import com.ondemandmonitoring.devicecheck.domain.PersistedPostDeviceCheck;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersistedPostDeviceCheckRepository extends JpaRepository<PersistedPostDeviceCheck, String> {

    List<PersistedPostDeviceCheck> findByMissionIdOrderByCreatedAtDesc(String missionId);

    Optional<PersistedPostDeviceCheck> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct run from PersistedPostDeviceCheck run "
            + "left join fetch run.items "
            + "where run.id = :id")
    Optional<PersistedPostDeviceCheck> findByIdForUpdate(@Param("id") String id);
}


