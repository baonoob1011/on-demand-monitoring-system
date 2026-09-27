package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.DeviceTelemetry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DroneTelemetryRepository extends JpaRepository<DeviceTelemetry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceTelemetry> findByDeviceCode(String deviceCode);

    @Query("select telemetry from DroneTelemetry telemetry where telemetry.droneCode = :droneCode")
    Optional<DeviceTelemetry> readByDroneCode(@Param("droneCode") String droneCode);

}
