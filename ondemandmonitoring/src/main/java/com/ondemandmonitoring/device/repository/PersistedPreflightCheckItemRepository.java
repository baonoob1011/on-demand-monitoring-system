package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.PersistedPreDeviceCheckItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistedPreflightCheckItemRepository extends JpaRepository<PersistedPreDeviceCheckItem, String> {

    Optional<PersistedPreDeviceCheckItem> findByPreflightCheckIdAndCheckType(
            String preflightCheckId,
            String checkType);
}

