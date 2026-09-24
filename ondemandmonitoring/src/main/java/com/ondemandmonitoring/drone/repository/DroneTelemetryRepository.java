package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DroneTelemetryRepository extends JpaRepository<DroneTelemetry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DroneTelemetry> findByDroneCode(String droneCode);

    @Query("select telemetry from DroneTelemetry telemetry where telemetry.droneCode = :droneCode")
    Optional<DroneTelemetry> readByDroneCode(@Param("droneCode") String droneCode);
}
