package com.ondemandmonitoring.mapillary;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Finds the best nearby Mapillary image for a position (and optional heading). */
@Service
public class MapillaryImageService {

    private final MapillaryClient client;
    private final MapillaryProperties properties;

    public MapillaryImageService(MapillaryClient client, MapillaryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Optional<MapillaryImageCandidate> findNearestImage(double latitude, double longitude) {
        return findNearestImage(latitude, longitude, null);
    }

    public Optional<MapillaryImageCandidate> findNearestImage(
            double latitude, double longitude, Double headingDegrees) {
        double radius = properties.getSearchRadiusMeters();
        List<MapillaryImageCandidate> candidates =
                client.searchNearby(latitude, longitude, radius, Math.max(1, properties.getSearchLimit()));
        return candidates.stream()
                .filter(candidate -> distanceMeters(latitude, longitude,
                        candidate.latitude(), candidate.longitude()) <= radius * 1.5)
                .min(Comparator.comparingDouble(candidate ->
                        score(candidate, latitude, longitude, headingDegrees, radius)));
    }

    public byte[] download(MapillaryImageCandidate candidate) {
        return client.download(candidate.thumbUrl());
    }

    /** Lower is better: distance, recency, heading match (if known) and non-panorama preferred. */
    double score(MapillaryImageCandidate candidate, double latitude, double longitude,
            Double headingDegrees, double radius) {
        double distance = Math.min(1.0,
                distanceMeters(latitude, longitude, candidate.latitude(), candidate.longitude())
                        / Math.max(1.0, radius));
        double recency = 0.5;
        if (candidate.capturedAt() != null) {
            double ageDays = Duration.between(candidate.capturedAt(), Instant.now()).toDays();
            recency = Math.min(1.0, Math.max(0.0, ageDays / (365.0 * 5)));
        }
        double heading = 0.0;
        if (headingDegrees != null && candidate.compassAngle() != null) {
            double diff = Math.abs(((headingDegrees - candidate.compassAngle()) % 360 + 540) % 360 - 180);
            heading = diff / 180.0;
        }
        double panorama = candidate.panorama() ? 1.0 : 0.0;
        return 0.55 * distance + 0.2 * recency + 0.15 * heading + 0.1 * panorama;
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
