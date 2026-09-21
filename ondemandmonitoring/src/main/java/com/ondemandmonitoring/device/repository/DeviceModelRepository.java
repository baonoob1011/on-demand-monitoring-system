package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.DeviceModel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceModelRepository extends JpaRepository<DeviceModel, String> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    Optional<DeviceModel> findByCode(String code);
}
