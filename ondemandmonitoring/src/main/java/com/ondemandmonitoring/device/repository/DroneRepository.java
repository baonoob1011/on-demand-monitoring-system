package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.Drone;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DroneRepository extends JpaRepository<Drone, String> {

    @Query("SELECT COUNT(d) > 0 FROM Drone d JOIN d.device dev WHERE dev.serialNumber = :serialNumber")
    boolean existsBySerialNumber(@Param("serialNumber") String serialNumber);

    @Query("SELECT COUNT(d) > 0 FROM Drone d JOIN d.device dev WHERE dev.serialNumber = :serialNumber AND d.id <> :id")
    boolean existsBySerialNumberAndIdNot(@Param("serialNumber") String serialNumber, @Param("id") String id);

    @Query("SELECT d FROM Drone d JOIN d.device dev WHERE dev.serialNumber = :deviceCode")
    Optional<Drone> findByDroneCode(@Param("deviceCode") String deviceCode);

    Optional<Drone> findByDeviceId(String deviceId);

    /**
     * Find the first available drone from the pool, used for auto-swap after BATTERY preflight failure.
     * Excludes a specific drone by id (the faulty one being replaced).
     */
    @Query("SELECT d FROM Drone d JOIN d.device dev WHERE dev.operationalStatus = :status AND d.id <> :excludeId ORDER BY d.createdAt ASC")
    Optional<Drone> findFirstAvailableExcluding(
            @Param("status") DeviceOperationalStatus status,
            @Param("excludeId") String excludeId
    );

    @Query("SELECT d FROM Drone d JOIN d.device dev WHERE " +
           "(:modelId IS NULL OR d.droneModel.id = :modelId) AND " +
           "(:payloadId IS NULL OR (d.dronePayload IS NOT NULL AND d.dronePayload.id = :payloadId)) AND " +
           "(:status IS NULL OR dev.operationalStatus = :status)")
    Page<Drone> searchDrones(
            @Param("modelId") String modelId,
            @Param("payloadId") String payloadId,
            @Param("status") DeviceOperationalStatus status,
            Pageable pageable
    );
}
