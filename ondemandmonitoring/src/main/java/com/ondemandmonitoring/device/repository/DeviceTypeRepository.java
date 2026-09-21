package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTypeRepository extends JpaRepository<DeviceType, String> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    Optional<DeviceType> findByCode(String code);
}
