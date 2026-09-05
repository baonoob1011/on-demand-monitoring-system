package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceTelemetry> findByDeviceCode(String deviceCode);
}
