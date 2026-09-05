package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.telemetry.DeviceTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, String> {
}
