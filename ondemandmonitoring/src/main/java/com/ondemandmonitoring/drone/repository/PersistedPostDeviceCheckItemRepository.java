package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.PersistedPostDeviceCheckItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistedPostDeviceCheckItemRepository extends JpaRepository<PersistedPostDeviceCheckItem, String> {

    Optional<PersistedPostDeviceCheckItem> findByPostDeviceCheckIdAndCheckType(
            String postDeviceCheckId,
            String checkType);
}

