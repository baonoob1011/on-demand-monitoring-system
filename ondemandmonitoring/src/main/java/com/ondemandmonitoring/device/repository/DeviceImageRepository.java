package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceImageRepository extends JpaRepository<DeviceImage, String> {

    List<DeviceImage> findByMissionIdOrderByCapturedAtDesc(String missionId);

    List<DeviceImage> findByMissionIdAndTypeOrderByCapturedAtDesc(String missionId, String type);

    Optional<DeviceImage> findByMissionIdAndDeviceIdAndLocalMediaId(
            String missionId, String deviceId, String localMediaId);

    Optional<DeviceImage> findByS3BucketAndS3Key(String s3Bucket, String s3Key);
}
