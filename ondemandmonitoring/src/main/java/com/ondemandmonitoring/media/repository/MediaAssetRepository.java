package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaAsset;
import java.util.List;
import java.util.Optional;
import com.ondemandmonitoring.media.domain.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaAsset m where m.id = :id")
    Optional<MediaAsset> findByIdForUpdate(@Param("id") String id);

    Optional<MediaAsset> findByMissionIdAndDroneCodeAndLocalMediaId(
            String missionId, String droneCode, String localMediaId);

    List<MediaAsset> findByMissionIdOrderByCapturedAtDesc(String missionId);

    List<MediaAsset> findByMissionIdInAndMediaStatusOrderByCapturedAtDesc(
            List<String> missionIds, MediaStatus status);

    List<MediaAsset> findByMissionIdAndTypeOrderByCapturedAtDesc(String missionId, String type);

    List<MediaAsset> findByDroneCodeOrderByCapturedAtDesc(String droneCode);

    List<MediaAsset> findByDroneCodeAndTypeOrderByCapturedAtDesc(String droneCode, String type);
}
