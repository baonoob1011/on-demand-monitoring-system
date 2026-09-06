package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceImageRepository extends JpaRepository<DeviceImage, String> {
}
