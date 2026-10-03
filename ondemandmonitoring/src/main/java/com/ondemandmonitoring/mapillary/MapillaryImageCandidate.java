package com.ondemandmonitoring.mapillary;

import java.time.Instant;

/** One Mapillary street-level image near a drone position. */
public record MapillaryImageCandidate(
        String imageId,
        double latitude,
        double longitude,
        Instant capturedAt,
        Double compassAngle,
        boolean panorama,
        String thumbUrl) {
}
