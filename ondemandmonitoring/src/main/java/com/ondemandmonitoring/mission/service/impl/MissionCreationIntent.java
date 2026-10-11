package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.dto.request.MissionCreateRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.UUID;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;

/** Versioned, length-prefixed business intent. Actor, expected revision and audit are excluded. */
public record MissionCreationIntent(String orderId, String key, String label,
        java.time.Instant start, java.time.Instant end, String replacesMissionId) {
    public static MissionCreationIntent from(MissionCreateRequest request) {
        if (request == null || request.getOrderId() == null || request.getOrderId().isBlank()
                || request.getIdempotencyKey() == null
                || !request.getIdempotencyKey().matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || request.getExpectedPlanRevision() == null || request.getExpectedPlanRevision() < 0
                || request.getScopeLabel() == null || request.getScopeLabel().isBlank()
                || request.getScopeLabel().length() > 255 || request.getScheduledStartAt() == null
                || request.getScheduledEndAt() == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        if (request.getTargetPoint() != null || request.getTargetArea() != null || request.getChecklistItemIds() != null)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Custom scope and checklist assignment creation are deferred");
        if (request.getReplacesMissionId() != null && (request.getReplacesMissionId().isBlank() || request.getReplacesMissionId().length() > 255))
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        return new MissionCreationIntent(request.getOrderId(), UUID.fromString(request.getIdempotencyKey()).toString(),
                Normalizer.normalize(request.getScopeLabel().strip().replaceAll("\\s+", " "), Normalizer.Form.NFC),
                request.getScheduledStartAt(), request.getScheduledEndAt(),
                request.getReplacesMissionId() == null ? null : request.getReplacesMissionId().strip());
    }

    public String hash(Geometry point, Geometry area) {
        // Preserve every Phase 4 hash for ordinary creation; version 2 binds replacement identity.
        StringBuilder canonical = new StringBuilder(replacesMissionId == null ? "mission-create:1;" : "mission-create:2;");
        for (String value : new String[] {orderId, label, start.toString(), end.toString(), geometry(point), geometry(area)})
            canonical.append(value.length()).append(':').append(value).append(';');
        if (replacesMissionId != null) canonical.append(replacesMissionId.length()).append(':').append(replacesMissionId).append(';');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String geometry(Geometry geometry) {
        if (geometry == null) return "null";
        Geometry normalized = geometry.copy(); normalized.normalize();
        return WKBWriter.toHex(new WKBWriter(2, true).write(normalized));
    }
}
