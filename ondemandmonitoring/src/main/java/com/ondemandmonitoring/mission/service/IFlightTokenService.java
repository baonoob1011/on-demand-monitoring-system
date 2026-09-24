package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.domain.FlightToken;

/**
 * Service interface for issuing and validating secure HMAC-SHA256 flight tokens (60-minute TTL)
 * for GCS sessions and drone flight control.
 */
public interface IFlightTokenService {

    /**
     * Issues a secure 60-minute (3600s) HMAC-SHA256 flight token.
     * Validates that the requesting operator matches the currently assigned operator for the mission.
     *
     * @param missionId The unique identifier of the mission
     * @param droneCode The device/drone identifier
     * @param operatorId The identifier of the requesting operator
     * @return {@link FlightToken} Entity containing tokenValue, issuedAt, and expiresAt
     */
    FlightToken issueFlightToken(String missionId, String droneCode, String operatorId);

    /**
     * Validates whether a token value exists, is unexpired, and is active (not revoked or used).
     *
     * @param tokenValue The token string to validate
     * @return {@code true} if valid and unexpired; {@code false} otherwise
     */
    boolean validateToken(String tokenValue);
}
