package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, String> {

    boolean existsBySerialNumber(String serialNumber);

    boolean existsBySerialNumberAndIdNot(String serialNumber, String id);

    Optional<Device> findBySerialNumber(String serialNumber);

    @Query("SELECT d FROM Device d WHERE " +
           "(:search IS NULL OR LOWER(d.serialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:modelId IS NULL OR d.deviceModel.id = :modelId)")
    Page<Device> searchDevices(
            @Param("search") String search,
            @Param("status") DeviceStatus status,
            @Param("modelId") String modelId,
            Pageable pageable
    );
}
