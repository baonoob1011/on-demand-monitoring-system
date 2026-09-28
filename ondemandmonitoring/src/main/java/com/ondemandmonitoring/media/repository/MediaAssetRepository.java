package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaAsset;
import java.util.List;
import java.util.Optional;
import com.ondemandmonitoring.media.domain.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {

        Optional<MediaAsset> findByMissionIdAndDeviceIdAndLocalMediaId(
                        String missionId, String deviceId, String localMediaId);

        List<MediaAsset> findByMissionIdOrderByCapturedAtDesc(String missionId);

        List<MediaAsset> findByMissionIdInAndMediaStatusOrderByCapturedAtDesc(
                        List<String> missionIds, MediaStatus status);

        List<MediaAsset> findByMissionIdAndTypeOrderByCapturedAtDesc(String missionId, String type);

        List<MediaAsset> findByDeviceIdOrderByCapturedAtDesc(String deviceId);

        List<MediaAsset> findByDeviceIdAndTypeOrderByCapturedAtDesc(String deviceId, String type);

}
