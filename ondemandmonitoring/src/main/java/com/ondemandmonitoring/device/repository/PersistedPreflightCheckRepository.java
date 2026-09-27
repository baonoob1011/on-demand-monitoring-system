package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.PreflightCheck;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersistedPreflightCheckRepository extends JpaRepository<PreflightCheck, String> {

    List<PreflightCheck> findByMissionIdOrderByCreatedAtDesc(String missionId);

    Optional<PreflightCheck> findFirstByMissionIdOrderByCreatedAtDesc(String missionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct run from PreflightCheck run "
            + "left join fetch run.items "
            + "where run.id = :id")
    Optional<PreflightCheck> findByIdForUpdate(@Param("id") String id);
}
