package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.Device;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, String> {

    boolean existsBySerialNumber(String serialNumber);

    boolean existsBySerialNumberAndIdNot(String serialNumber, String id);

    Optional<Device> findBySerialNumber(String serialNumber);
}
