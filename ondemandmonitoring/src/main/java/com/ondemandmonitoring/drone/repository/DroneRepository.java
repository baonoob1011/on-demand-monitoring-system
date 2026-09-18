package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DroneRepository extends JpaRepository<Drone, String> {

    boolean existsBySerialNumber(String serialNumber);

    boolean existsBySerialNumberAndIdNot(String serialNumber, String id);

    Optional<Drone> findByDroneCode(String droneCode);

    @Query("SELECT d FROM Drone d WHERE " +
           "(:modelId IS NULL OR d.droneModel.id = :modelId) AND " +
           "(:payloadId IS NULL OR (d.dronePayload IS NOT NULL AND d.dronePayload.id = :payloadId)) AND " +
           "(:status IS NULL OR d.status = :status)")
    Page<Drone> searchDrones(
            @Param("modelId") String modelId,
            @Param("payloadId") String payloadId,
            @Param("status") DroneStatus status,
            Pageable pageable
    );
}
