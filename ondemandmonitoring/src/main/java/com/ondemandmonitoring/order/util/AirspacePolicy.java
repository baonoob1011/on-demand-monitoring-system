package com.ondemandmonitoring.order.util;

import java.util.List;
import java.util.Optional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

/**
 * Real-world (WGS84) airspace areas where a drone flight needs a permit.
 * Mirrors airspace.ts on the web client; keep both lists in sync.
 */
public final class AirspacePolicy {

    public record PermitZone(String code, String name, double latitude, double longitude, double radiusM) {
    }

    private static final List<PermitZone> PERMIT_ZONES = List.of(
            new PermitZone("TAN_SON_NHAT", "Tan Son Nhat Airport", 10.8188, 106.6520, 3_000));

    private static final double EARTH_RADIUS_M = 6_371_000;

    private AirspacePolicy() {
    }

    public static List<PermitZone> zones() {
        return PERMIT_ZONES;
    }

    /** First permit zone touched by the point or any vertex of the coverage area. */
    public static Optional<PermitZone> findPermitZone(Point point, Geometry coverageArea) {
        for (PermitZone zone : PERMIT_ZONES) {
            if (point != null && distanceM(zone, point.getY(), point.getX()) <= zone.radiusM()) {
                return Optional.of(zone);
            }
            if (coverageArea != null) {
                for (Coordinate c : coverageArea.getCoordinates()) {
                    if (distanceM(zone, c.y, c.x) <= zone.radiusM()) {
                        return Optional.of(zone);
                    }
                }
            }
        }
        return Optional.empty();
    }

    static double distanceM(PermitZone zone, double latitude, double longitude) {
        double dLat = Math.toRadians(latitude - zone.latitude());
        double dLon = Math.toRadians(longitude - zone.longitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(zone.latitude())) * Math.cos(Math.toRadians(latitude))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
