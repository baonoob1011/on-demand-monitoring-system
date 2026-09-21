package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTypeRepository extends JpaRepository<DeviceType, String> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    Optional<DeviceType> findByCode(String code);

    @Query("SELECT d FROM DeviceType d WHERE " +
           "(:search IS NULL OR LOWER(d.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(d.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<DeviceType> searchDeviceTypes(@Param("search") String search, Pageable pageable);
}
