package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {

    List<MediaAsset> findByMissionIdOrderByCapturedAtDesc(String missionId);

    List<MediaAsset> findByMissionIdAndTypeOrderByCapturedAtDesc(String missionId, String type);

    List<MediaAsset> findByDeviceCodeOrderByCapturedAtDesc(String deviceCode);

    List<MediaAsset> findByDeviceCodeAndTypeOrderByCapturedAtDesc(String deviceCode, String type);
}
