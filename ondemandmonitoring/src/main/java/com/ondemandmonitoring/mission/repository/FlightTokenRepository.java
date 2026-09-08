package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.FlightToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FlightTokenRepository extends JpaRepository<FlightToken, String> {

    Optional<FlightToken> findByMissionIdAndUsedFalseAndRevokedFalse(String missionId);

    Optional<FlightToken> findByTokenValue(String tokenValue);
}
