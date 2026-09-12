package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceImageRepository extends JpaRepository<DeviceImage, String> {

    List<DeviceImage> findByMissionIdOrderByCapturedAtDesc(String missionId);

    List<DeviceImage> findByMissionIdAndTypeOrderByCapturedAtDesc(String missionId, String type);
}
