package com.ondemandmonitoring.flightarea.dto;

import java.util.List;

/** Aggregate preliminary flight-area assessment. Advisory only; never approves or rejects an order. */
public record FlightAreaAssessmentResponse(
        Location location,
        Elevation elevation,
        RestrictedZones restrictedZones,
        OsmContext osmContext,
        Assessment assessment) {

    public record Location(double latitude, double longitude, double radiusMeters) {
    }

    /**
     * Terrain elevation is metres above mean sea level (AMSL). The requested altitude is metres above
     * ground level (AGL). The estimate adds them; the two are never interchangeable.
     */
    public record Elevation(
            boolean available,
            Double terrainElevationMeters,
            String reference,
            Double requestedAltitudeAglMeters,
            Double estimatedFlightAltitudeAmslMeters,
            String provider,
            String errorCode) {
    }

    public record RestrictedZones(
            /** False when the system has no GPS-referenced restricted zone at all to compare against. */
            boolean dataAvailable,
            boolean pointInsideRestrictedZone,
            boolean monitoringAreaIntersectsRestrictedZone,
            Double nearestRestrictedZoneDistanceMeters,
            List<AffectedZone> affectedZones) {
    }

    public record AffectedZone(
            String id,
            String code,
            String name,
            String zoneType,
            String purpose,
            boolean restricted,
            Double distanceMeters,
            boolean intersectsMonitoringArea) {
    }

    public record OsmContext(
            boolean available,
            String errorCode,
            Double queryRadiusMeters,
            int buildingCount,
            int towerCount,
            int mastCount,
            int powerTowerCount,
            boolean aerodromeNearby,
            boolean helipadNearby,
            List<NearbyFeature> importantFeatures) {
    }

    /** {@code heightMeters} is only ever a value OpenStreetMap actually carries; otherwise null. */
    public record NearbyFeature(
            String type,
            String name,
            Double distanceMeters,
            Double heightMeters,
            Double latitude,
            Double longitude,
            String source) {
    }

    public record Assessment(
            FlightAreaRiskLevel level,
            List<AssessmentFinding> findings,
            String disclaimer) {
    }

    public record AssessmentFinding(String code, FindingSeverity severity, String message) {
    }

    public enum FlightAreaRiskLevel {
        FAVORABLE,
        NEEDS_REVIEW,
        HIGH_RISK
    }

    public enum FindingSeverity {
        INFO,
        WARNING,
        HIGH
    }
}
