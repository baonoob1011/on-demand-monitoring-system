package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.PreflightCheckItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistedPreflightCheckItemRepository extends JpaRepository<PreflightCheckItem, String> {

    Optional<PreflightCheckItem> findByPreflightCheckIdAndCheckType(
            String preflightCheckId,
            String checkType);
}
