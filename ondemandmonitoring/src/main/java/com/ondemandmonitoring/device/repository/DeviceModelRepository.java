package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceModel;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceModelRepository extends JpaRepository<DeviceModel, String> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    Optional<DeviceModel> findByCode(String code);

    @Query("SELECT DISTINCT d FROM DeviceModel d LEFT JOIN d.deviceTypes dt WHERE " +
           "(:search IS NULL OR LOWER(d.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(d.modelName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(d.manufacturer) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:deviceTypeId IS NULL OR dt.id = :deviceTypeId)")
    Page<DeviceModel> searchDeviceModels(@Param("search") String search, @Param("deviceTypeId") String deviceTypeId, Pageable pageable);
}
