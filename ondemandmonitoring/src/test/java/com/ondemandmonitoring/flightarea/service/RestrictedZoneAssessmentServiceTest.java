package com.ondemandmonitoring.flightarea.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.RestrictedZones;
import com.ondemandmonitoring.flightarea.repository.RestrictedZoneSpatialRepository;
import com.ondemandmonitoring.flightarea.repository.RestrictedZoneSpatialRepository.ZoneDistance;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Interpretation of the spatial rows; the SQL itself is covered by RestrictedZoneSpatialRepositoryPostgresTest. */
class RestrictedZoneAssessmentServiceTest {

    private RestrictedZoneSpatialRepository repository;
    private RestrictedZoneAssessmentService service;

    @BeforeEach
    void setUp() {
        repository = mock(RestrictedZoneSpatialRepository.class);
        service = new RestrictedZoneAssessmentService(repository, new FlightAreaProperties());
    }

    private static ZoneDistance row(String code, double distance, boolean inside) {
        return new ZoneDistance("id-" + code, code, "Zone " + code, "RESTRICTED", "purpose", inside, distance);
    }

    @Test
    void aPointOutsideEveryZoneIsClear() {
        when(repository.findRestrictedZonesWithin(10.6, 106.7, 500.0)).thenReturn(List.of());
        when(repository.findNearestRestrictedDistance(10.6, 106.7)).thenReturn(8500.04);

        RestrictedZones result = service.assess(10.6, 106.7, 300);

        assertTrue(result.dataAvailable());
        assertFalse(result.pointInsideRestrictedZone());
        assertFalse(result.monitoringAreaIntersectsRestrictedZone());
        assertEquals(8500.0, result.nearestRestrictedZoneDistanceMeters());
        assertTrue(result.affectedZones().isEmpty());
    }

    @Test
    void aPointInsideAZoneIsInsideAndIntersecting() {
        when(repository.findRestrictedZonesWithin(10.8, 106.65, 500.0)).thenReturn(List.of(row("TSN", 0.0, true)));
        when(repository.findNearestRestrictedDistance(10.8, 106.65)).thenReturn(0.0);

        RestrictedZones result = service.assess(10.8, 106.65, 300);

        assertTrue(result.pointInsideRestrictedZone());
        assertTrue(result.monitoringAreaIntersectsRestrictedZone());
        assertEquals(0.0, result.nearestRestrictedZoneDistanceMeters());
        assertTrue(result.affectedZones().get(0).intersectsMonitoringArea());
    }

    @Test
    void aCircleTouchingAZoneIntersectsWithoutThePointBeingInside() {
        when(repository.findRestrictedZonesWithin(10.8, 106.7, 500.0))
                .thenReturn(List.of(row("TSN", 250.0, false), row("OTHER", 900.0, false)));
        when(repository.findNearestRestrictedDistance(10.8, 106.7)).thenReturn(250.0);

        RestrictedZones result = service.assess(10.8, 106.7, 300);

        assertFalse(result.pointInsideRestrictedZone());
        assertTrue(result.monitoringAreaIntersectsRestrictedZone());
        assertEquals(2, result.affectedZones().size());
        assertTrue(result.affectedZones().get(0).intersectsMonitoringArea());
        assertFalse(result.affectedZones().get(1).intersectsMonitoringArea());
    }

    @Test
    void aNearbyZoneOutsideTheCircleDoesNotIntersect() {
        when(repository.findRestrictedZonesWithin(10.8, 106.7, 500.0)).thenReturn(List.of(row("TSN", 850.0, false)));
        when(repository.findNearestRestrictedDistance(10.8, 106.7)).thenReturn(850.0);

        RestrictedZones result = service.assess(10.8, 106.7, 300);

        assertFalse(result.monitoringAreaIntersectsRestrictedZone());
        assertEquals(850.0, result.nearestRestrictedZoneDistanceMeters());
    }

    @Test
    void theSearchRadiusIsTheLargerOfTheCircleAndTheWarningDistance() {
        when(repository.findRestrictedZonesWithin(10.8, 106.7, 3000.0)).thenReturn(List.of());

        service.assess(10.8, 106.7, 3000);

        verify(repository).findRestrictedZonesWithin(10.8, 106.7, 3000.0);
    }

    @Test
    void noGpsZoneAtAllIsReportedAsMissingData() {
        when(repository.findRestrictedZonesWithin(10.6, 106.7, 500.0)).thenReturn(List.of());
        when(repository.findNearestRestrictedDistance(10.6, 106.7)).thenReturn(null);

        RestrictedZones result = service.assess(10.6, 106.7, 300);

        assertFalse(result.dataAvailable());
        assertNull(result.nearestRestrictedZoneDistanceMeters());
    }
}
