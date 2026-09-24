package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.DronePayload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DronePayloadRepository extends JpaRepository<DronePayload, String> {

    boolean existsByModelNameIgnoreCase(String modelName);

    @Query("SELECT p FROM DronePayload p WHERE " +
           "(:sensorType IS NULL OR LOWER(p.sensorType) LIKE LOWER(CONCAT('%', :sensorType, '%'))) AND " +
           "(:modelName IS NULL OR LOWER(p.modelName) LIKE LOWER(CONCAT('%', :modelName, '%')))")
    Page<DronePayload> searchDronePayloads(
            @Param("sensorType") String sensorType,
            @Param("modelName") String modelName,
            Pageable pageable
    );
}
