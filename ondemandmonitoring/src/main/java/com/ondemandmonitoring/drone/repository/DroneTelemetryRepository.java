package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DroneTelemetryRepository extends JpaRepository<DroneTelemetry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DroneTelemetry> findByDroneCode(String droneCode);
}
