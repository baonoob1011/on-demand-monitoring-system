package com.ondemandmonitoring.mission.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FlightTokenResponse {
    String id;
    String missionId;
    String deviceCode;
    String operatorId;
    String tokenValue;
    Instant issuedAt;
    Instant expiresAt;
    boolean used;
    boolean revoked;
}
