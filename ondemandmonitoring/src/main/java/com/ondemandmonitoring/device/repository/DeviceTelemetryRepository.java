package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, String> {

    Optional<DeviceTelemetry> findTopByDeviceConnectionIdOrderByRecordedAtDesc(String deviceConnectionId);
}

