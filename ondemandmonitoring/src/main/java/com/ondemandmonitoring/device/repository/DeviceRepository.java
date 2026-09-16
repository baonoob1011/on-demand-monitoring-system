package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.Device;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, String> {

    Optional<Device> findByDeviceCode(String deviceCode);

    java.util.List<Device> findByStatus(com.ondemandmonitoring.device.enums.DeviceStatus status);
}
