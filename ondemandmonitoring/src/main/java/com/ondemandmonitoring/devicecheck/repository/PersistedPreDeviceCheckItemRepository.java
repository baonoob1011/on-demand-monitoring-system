package com.ondemandmonitoring.devicecheck.repository;

import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheckItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistedPreDeviceCheckItemRepository extends JpaRepository<PersistedPreDeviceCheckItem, String> {

    Optional<PersistedPreDeviceCheckItem> findByPreDeviceCheckIdAndCheckType(
            String preDeviceCheckId,
            String checkType);
}
