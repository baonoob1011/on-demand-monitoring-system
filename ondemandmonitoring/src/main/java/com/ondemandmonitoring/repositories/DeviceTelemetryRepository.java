package com.ondemandmonitoring.repositories;

import com.ondemandmonitoring.entities.DeviceTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, String> {
}
