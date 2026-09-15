package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaAsset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {

    List<MediaAsset> findByMissionIdOrderByCapturedAtDesc(String missionId);

    List<MediaAsset> findByMissionIdAndTypeOrderByCapturedAtDesc(String missionId, String type);

    List<MediaAsset> findByDeviceCodeOrderByCapturedAtDesc(String deviceCode);

    List<MediaAsset> findByDeviceCodeAndTypeOrderByCapturedAtDesc(String deviceCode, String type);

    Optional<MediaAsset> findByMissionIdAndDeviceIdAndLocalMediaId(
            String missionId, String deviceId, String localMediaId);

    Optional<MediaAsset> findByS3BucketAndS3Key(String s3Bucket, String s3Key);
}
