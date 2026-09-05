package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.media.DeviceImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceImageRepository extends JpaRepository<DeviceImage, String> {
}
