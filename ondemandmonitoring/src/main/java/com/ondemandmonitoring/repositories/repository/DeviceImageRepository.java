package com.ondemandmonitoring.repositories.repository;

import com.ondemandmonitoring.entities.DeviceImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceImageRepository extends JpaRepository<DeviceImage, String> {
}
