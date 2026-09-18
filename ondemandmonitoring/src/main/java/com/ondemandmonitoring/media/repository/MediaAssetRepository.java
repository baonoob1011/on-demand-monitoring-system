package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {

    List<MediaAsset> findByMissionIdOrderByCapturedAtDesc(String missionId);

    List<MediaAsset> findByMissionIdAndTypeOrderByCapturedAtDesc(String missionId, String type);

    List<MediaAsset> findByDroneCodeOrderByCapturedAtDesc(String droneCode);

    List<MediaAsset> findByDroneCodeAndTypeOrderByCapturedAtDesc(String droneCode, String type);
}
