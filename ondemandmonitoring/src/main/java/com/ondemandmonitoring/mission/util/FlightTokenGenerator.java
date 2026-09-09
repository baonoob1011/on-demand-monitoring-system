package com.ondemandmonitoring.mission.util;

import lombok.experimental.UtilityClass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Enterprise Flight-Access Token Generator.
 *
 * Generates cryptographically signed, structured tokens following UTM / FAA LAANC standards.
 * Format: FTK-[MISSION_ID]-[DEVICE_CODE]-[TIMESTAMP_HEX]-[HMAC_SHA256_SIGNATURE_8]
 * Example: FTK-M001-DRONE01-191C80B40-A89F4C1B
 */
@UtilityClass
public class FlightTokenGenerator {

    private static final String SECRET_KEY = "OnDemandMonitoringSystem#EnterpriseFlightAccessKey2026";
    private static final String HMAC_ALGORITHM = "HmacSHA256";


    /**
     * Generates an Enterprise Flight Access Token.
     *
     * @param missionId  Target mission ID (e.g. "M-001")
     * @param deviceCode Assigned drone code (e.g. "DRONE-01")
     * @param operatorId Operator ID issuing token (e.g. "OP-001")
     * @param issuedAt   Timestamp when token was issued
     * @return Cryptographically signed structured token string
     */
    public static String generateTokenValue(String missionId, String deviceCode, String operatorId, Instant issuedAt) {
        String cleanMission = sanitize(missionId);
        String cleanDevice = sanitize(deviceCode);
        long epochSecond = issuedAt != null ? issuedAt.getEpochSecond() : Instant.now().getEpochSecond();
        String hexTimestamp = Long.toHexString(epochSecond).toUpperCase();

        String rawPayload = String.format("%s:%s:%s:%d", cleanMission, cleanDevice,
                operatorId != null ? operatorId : "OP-SYSTEM", epochSecond);
        String signature = computeHmacSha256(rawPayload, SECRET_KEY).substring(0, 8).toUpperCase();

        return String.format("FTK-%s-%s-%s-%s", cleanMission, cleanDevice, hexTimestamp, signature);
    }

    private static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "UNKNOWN";
        }
        return input.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }

    private static String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature for flight token", e);
        }
    }
}
