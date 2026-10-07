package com.ondemandmonitoring.flightarea.service;

import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.AffectedZone;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.RestrictedZones;
import com.ondemandmonitoring.flightarea.repository.RestrictedZoneSpatialRepository;
import com.ondemandmonitoring.flightarea.repository.RestrictedZoneSpatialRepository.ZoneDistance;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares a monitoring circle with the system's restricted zones using PostGIS. Only zones with real
 * GPS coordinates take part (see {@link RestrictedZoneSpatialRepository}); this is internal data, so a
 * failure here is a real error and is not swallowed.
 */
@Service
@RequiredArgsConstructor
public class RestrictedZoneAssessmentService {

    private final RestrictedZoneSpatialRepository repository;
    private final FlightAreaProperties properties;

    @Transactional(readOnly = true)
    public RestrictedZones assess(double latitude, double longitude, double radiusMeters) {
        double searchRadius = Math.max(radiusMeters, properties.getRestrictedZoneWarningDistanceM());
        List<ZoneDistance> nearby = repository.findRestrictedZonesWithin(latitude, longitude, searchRadius);
        Double nearest = repository.findNearestRestrictedDistance(latitude, longitude);

        List<AffectedZone> affected = nearby.stream()
                .map(zone -> new AffectedZone(
                        zone.id(),
                        zone.code(),
                        zone.name(),
                        zone.zoneType(),
                        zone.purpose(),
                        true,
                        round1(zone.distanceMeters()),
                        zone.distanceMeters() <= radiusMeters))
                .toList();
        boolean pointInside = nearby.stream().anyMatch(ZoneDistance::pointInside);
        boolean intersects = pointInside || affected.stream().anyMatch(AffectedZone::intersectsMonitoringArea);
        return new RestrictedZones(
                nearest != null,
                pointInside,
                intersects,
                nearest == null ? null : round1(nearest),
                affected);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
