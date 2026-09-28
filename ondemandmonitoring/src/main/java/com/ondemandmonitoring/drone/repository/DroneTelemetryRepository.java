package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.DeviceTelemetry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DroneTelemetryRepository extends JpaRepository<DeviceTelemetry, String> {

    Optional<DeviceTelemetry> findTopByDeviceConnectionIdOrderByRecordedAtDesc(String deviceConnectionId);
}
