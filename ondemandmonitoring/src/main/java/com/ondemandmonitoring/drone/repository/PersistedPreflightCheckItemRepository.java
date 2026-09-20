package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.PersistedPreflightCheckItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistedPreflightCheckItemRepository extends JpaRepository<PersistedPreflightCheckItem, String> {

    Optional<PersistedPreflightCheckItem> findByPreflightCheckIdAndCheckType(
            String preflightCheckId,
            String checkType);
}
