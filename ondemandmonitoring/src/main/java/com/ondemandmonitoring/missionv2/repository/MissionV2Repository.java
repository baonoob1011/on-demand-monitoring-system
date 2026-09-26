package com.ondemandmonitoring.missionv2.repository;

import com.ondemandmonitoring.missionv2.domain.MissionV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MissionV2Repository extends JpaRepository<MissionV2, String> {
    Optional<MissionV2> findByOrderId(String orderId);

    boolean existsByMissionCode(String code);

    boolean existByOrderId(String orderId);
}
