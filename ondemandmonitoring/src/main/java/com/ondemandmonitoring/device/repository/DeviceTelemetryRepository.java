package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceTelemetry> findByDeviceCode(String deviceCode);

    @Query("select telemetry from DeviceTelemetry telemetry where telemetry.deviceCode = :deviceCode")
    Optional<DeviceTelemetry> readByDeviceCode(@Param("deviceCode") String deviceCode);

    default Optional<DeviceTelemetry> findByDroneCode(String droneCode) {
        return findByDeviceCode(droneCode);
    }

    default Optional<DeviceTelemetry> readByDroneCode(String droneCode) {
        return readByDeviceCode(droneCode);
    }
}
