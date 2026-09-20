package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.SimulationPlanningEnvironment;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimulationPlanningEnvironmentTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 0);

    @Mock
    private ZoneRepository zoneRepository;

    private SimulationPlanningEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new SimulationPlanningEnvironment(zoneRepository, planningGrid());
    }

    @Test
    void gridMetadataUsesCurrentSimulationCoordinates() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of());

        EnvironmentSample sample = environment.sample(0.0, 0.0);

        assertThat(sample.insideWorldBounds()).isTrue();
        assertThat(sample.terrainElevationM()).isEqualTo(10.0);
        assertThat(sample.surfaceElevationM()).isEqualTo(10.0);
    }

    @Test
    void xyToCellUsesNearestPhysicalGazeboCoordinateWithoutScreenYInversion() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of());

        EnvironmentSample min = environment.sample(-10.0, -10.0);
        EnvironmentSample center = environment.sample(0.0, 0.0);
        EnvironmentSample max = environment.sample(10.0, 10.0);

        assertThat(min.terrainElevationM()).isEqualTo(1.0);
        assertThat(center.terrainElevationM()).isEqualTo(10.0);
        assertThat(max.terrainElevationM()).isEqualTo(21.0);
    }

    @Test
    void sampleInsideWorldOutsideRestrictedZoneIsAllowed() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of(airportZone()));

        EnvironmentSample sample = environment.sample(0.0, 0.0);

        assertThat(sample.insideWorldBounds()).isTrue();
        assertThat(sample.restricted()).isFalse();
        assertThat(sample.restrictedZoneCode()).isNull();
    }

    @Test
    void sampleInsideRestrictedAirportReturnsZoneLabel() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of(airportZone()));

        EnvironmentSample sample = environment.sample(-5.0, -5.0);

        assertThat(sample.insideWorldBounds()).isTrue();
        assertThat(sample.restricted()).isTrue();
        assertThat(sample.restrictedZoneCode()).isEqualTo("AIRPORT");
        assertThat(sample.restrictedZoneName()).isEqualTo("Airport");
    }

    @Test
    void restrictedZoneBoundaryIsTreatedAsRestricted() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of(airportZone()));

        EnvironmentSample sample = environment.sample(-8.0, -5.0);

        assertThat(sample.restricted()).isTrue();
        assertThat(sample.restrictedZoneCode()).isEqualTo("AIRPORT");
    }

    @Test
    void worldBoundsAreInclusiveAndRejectOutsideCoordinates() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of());

        assertThat(environment.sample(-10.0, -10.0).insideWorldBounds()).isTrue();
        assertThat(environment.sample(10.0, 10.0).insideWorldBounds()).isTrue();
        assertThat(environment.sample(-10.01, 0.0).insideWorldBounds()).isFalse();
        assertThat(environment.sample(0.0, 10.01).insideWorldBounds()).isFalse();
    }

    @Test
    void nonCurrentWorldRestrictedZoneIsIgnored() {
        Zone airport = airportZone();
        airport.setSourceWorld("legacy_world");
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of(airport));

        EnvironmentSample sample = environment.sample(-5.0, -5.0);

        assertThat(sample.restricted()).isFalse();
    }

    @Test
    void sampleReflectsUpdatedDatabaseZones() {
        Zone firstAirport = airportZone();
        Zone movedAirport = airportZone();
        movedAirport.setPolygon(rectangle(6.0, 6.0, 9.0, 9.0));
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc())
                .thenReturn(List.of(firstAirport))
                .thenReturn(List.of(movedAirport));

        assertThat(environment.sample(-5.0, -5.0).restricted()).isTrue();
        assertThat(environment.sample(-5.0, -5.0).restricted()).isFalse();
    }

    @Test
    void terrainAndObstacleDataComeFromPlanningGrid() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of());

        EnvironmentSample sample = environment.sample(10.0, 10.0);

        assertThat(sample.terrainElevationM()).isEqualTo(21.0);
        assertThat(sample.obstacleHeightM()).isEqualTo(9.0);
        assertThat(sample.surfaceElevationM()).isEqualTo(30.0);
    }

    private Zone airportZone() {
        Zone zone = new Zone();
        zone.setCode("AIRPORT");
        zone.setName("Airport");
        zone.setZoneType("AIRPORT");
        zone.setPurpose("Runway and aircraft operating area inspection");
        zone.setRestricted(true);
        zone.setCenterXM(-5.0);
        zone.setCenterYM(-5.0);
        zone.setRadiusM(5.0);
        zone.setSourceWorld("test_world");
        zone.setCoordinateSystem("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        zone.setPolygon(rectangle(-8.0, -8.0, -2.0, -2.0));
        return zone;
    }

    private PlanningGrid planningGrid() {
        return new PlanningGrid(
                "test_world",
                "LOCAL_SIMULATION_METERS_GAZEBO_XY",
                new PlanningGrid.Bounds(-10.0, 10.0, -10.0, 10.0),
                10.0,
                3,
                3,
                "nearest-cell",
                List.of(
                        1.0, 2.0, 3.0,
                        9.0, 10.0, 11.0,
                        19.0, 20.0, 21.0),
                List.of(
                        0.0, 0.0, 0.0,
                        0.0, 0.0, 0.0,
                        0.0, 0.0, 9.0),
                List.of(
                        1.0, 2.0, 3.0,
                        9.0, 10.0, 11.0,
                        19.0, 20.0, 30.0));
    }

    private Polygon rectangle(double minX, double minY, double maxX, double maxY) {
        Coordinate[] ring = {
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        };
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(GEOMETRY_FACTORY.createLinearRing(ring));
        polygon.setSRID(0);
        return polygon;
    }
}
