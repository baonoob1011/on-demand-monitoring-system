package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;
import com.ondemandmonitoring.mission.service.IFlightTokenService;
import com.ondemandmonitoring.mission.util.FlightTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Dedicated service for issuing 60-minute HMAC-SHA256 flight tokens and validating operator authorization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightTokenService implements IFlightTokenService {

    public static final long TOKEN_TTL_SECONDS = 3600; // 60 minutes TTL

    private final FlightTokenRepository flightTokenRepository;
    private final MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;

    @Override
    @Transactional
    public FlightToken issueFlightToken(String missionId, String droneCode, String operatorId) {
        if (operatorId != null && !operatorId.isBlank()) {
            missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                    .ifPresent(assignment -> {
                        if (assignment.getOperatorId() != null && !assignment.getOperatorId().equals(operatorId)) {
                            throw new ApiException(ErrorCode.INVALID_REQUEST,
                                    "Operator " + operatorId + " is not assigned to mission " + missionId);
                        }
                    });
        }
        Instant now = Instant.now();
        FlightToken token = new FlightToken();
        token.setTokenValue(FlightTokenGenerator.generateTokenValue(missionId, droneCode, operatorId, now));
        token.setMissionId(missionId);
        token.setDroneCode(droneCode);
        token.setOperatorId(operatorId);
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusSeconds(TOKEN_TTL_SECONDS));
        token.setUsed(false);
        token.setRevoked(false);
        log.info("[TOKEN-ISSUED] Issued 60-min HMAC-SHA256 flight token for mission {}, device {}, operator {}",
                missionId, droneCode, operatorId);
        return flightTokenRepository.save(token);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        return flightTokenRepository.findByTokenValue(tokenValue)
                .map(FlightToken::isValid)
                .orElse(false);
    }
}
