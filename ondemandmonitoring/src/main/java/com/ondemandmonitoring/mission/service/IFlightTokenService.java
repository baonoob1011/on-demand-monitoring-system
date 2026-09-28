package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.domain.FlightToken;

/**
 * Service interface for issuing and validating secure HMAC-SHA256 flight tokens (60-minute TTL)
 * for GCS sessions and device control.
 */
public interface IFlightTokenService {

    /**
     * Issues a secure 60-minute (3600s) HMAC-SHA256 flight token.
     * Validates that the requesting staff member matches the current staff assignment for the mission.
     *
     * @param missionId The unique identifier of the mission
     * @param deviceId The device identifier
     * @param staffId The identifier of the requesting staff member
     * @return {@link FlightToken} Entity containing tokenValue, issuedAt, and expiresAt
     */
    FlightToken issueFlightToken(String missionId, String deviceId, String staffId);

    /**
     * Validates whether a token value exists, is unexpired, and is active (not revoked or used).
     *
     * @param tokenValue The token string to validate
     * @return {@code true} if valid and unexpired; {@code false} otherwise
     */
    boolean validateToken(String tokenValue);
}
