package com.ondemandmonitoring.mission.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FlightTokenGeneratorTest {

    @Test
    @DisplayName("Should generate valid structured enterprise flight token with FTK prefix and HMAC signature")
    void testGenerateTokenValueFormat() {
        Instant now = Instant.now();
        String token = FlightTokenGenerator.generateTokenValue("M-001", "DRONE-01", "OP-001", now);

        assertNotNull(token);
        assertTrue(token.startsWith("FTK-M001-DRONE01-"), "Token must start with FTK-MISSION-DEVICE prefix");

        String[] parts = token.split("-");
        assertEquals(5, parts.length, "Enterprise token format must contain 5 hyphens-separated segments: FTK, Mission, Device, Timestamp, Signature");
        assertEquals("FTK", parts[0]);
        assertEquals("M001", parts[1]);
        assertEquals("DRONE01", parts[2]);
        assertEquals(8, parts[4].length(), "HMAC-SHA256 signature segment must be 8 characters uppercase hex");
    }

    @Test
    @DisplayName("Should generate identical signatures for identical parameters and timestamp")
    void testDeterministicSignature() {
        Instant fixedTime = Instant.ofEpochSecond(1757402375);
        String token1 = FlightTokenGenerator.generateTokenValue("M-001", "DRONE-01", "OP-001", fixedTime);
        String token2 = FlightTokenGenerator.generateTokenValue("M-001", "DRONE-01", "OP-001", fixedTime);

        assertEquals(token1, token2, "Tokens generated with identical parameters must match");
    }

    @Test
    @DisplayName("Should generate different signatures for different missions or operators")
    void testDifferentParametersDifferentSignatures() {
        Instant fixedTime = Instant.ofEpochSecond(1757402375);
        String token1 = FlightTokenGenerator.generateTokenValue("M-001", "DRONE-01", "OP-001", fixedTime);
        String token2 = FlightTokenGenerator.generateTokenValue("M-002", "DRONE-01", "OP-001", fixedTime);

        assertNotEquals(token1, token2, "Tokens for different missions must yield different cryptographic signatures");
    }
}
