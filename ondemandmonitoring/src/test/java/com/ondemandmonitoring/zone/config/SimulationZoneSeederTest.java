package com.ondemandmonitoring.zone.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.environment.repository.AtmosphereProfileRepository;
import com.ondemandmonitoring.zone.domain.ThermalSource;
import com.ondemandmonitoring.zone.enums.ThermalType;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.repository.SimulationMapFeatureRepository;
import com.ondemandmonitoring.zone.repository.ThermalSourceRepository;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

class SimulationZoneSeederTest {

    private final ZoneRepository zoneRepository = mock(ZoneRepository.class);
    private final ThermalSourceRepository thermalSourceRepository = mock(ThermalSourceRepository.class);
    private final Map<String, ThermalSource> persisted = new LinkedHashMap<>();

    private SimulationZoneSeeder seeder;
    private Zone forest;

    @BeforeEach
    void setUp() {
        seeder = new SimulationZoneSeeder(
                zoneRepository,
                mock(SimulationMapFeatureRepository.class),
                thermalSourceRepository,
                mock(AtmosphereProfileRepository.class),
                mock(JdbcTemplate.class),
                mock(Environment.class));

        SimulationZoneSeeder.SimulationZone forestSeed = zoneSeed("FOREST_MONITORING_AREA");
        forest = toEntity(forestSeed);
        when(zoneRepository.findByCode("FOREST_MONITORING_AREA")).thenReturn(Optional.of(forest));
        when(thermalSourceRepository.findByCode(any())).thenAnswer(invocation ->
                Optional.ofNullable(persisted.get(invocation.getArgument(0))));
        when(thermalSourceRepository.count()).thenAnswer(invocation -> (long) persisted.size());
        when(thermalSourceRepository.save(any(ThermalSource.class))).thenAnswer(invocation -> {
            ThermalSource source = invocation.getArgument(0);
            persisted.put(source.getCode(), source);
            return source;
        });
    }

    @Test
    void seedsThermalSourcesAndRepeatedSeedDoesNotCreateDuplicates() {
        seeder.seedThermalSources();
        seeder.seedThermalSources();

        assertThat(persisted).hasSize(5);
        assertThat(persisted.values()).allSatisfy(source -> {
            assertThat(source.getZone()).isSameAs(forest);
            assertThat(source.isActive()).isTrue();
        });
    }

    @Test
    void repeatedThermalSeedDoesNotOverwriteDatabaseEdits() {
        seeder.seedThermalSources();
        ThermalSource edited = persisted.get("FOREST_HOTSPOT_01");
        edited.setCenterXM(123.45);
        edited.setCenterYM(-67.89);
        edited.setRadiusM(44.0);
        edited.setTemperatureC(321.0);

        seeder.seedThermalSources();

        assertThat(persisted.get("FOREST_HOTSPOT_01").getCenterXM()).isEqualTo(123.45);
        assertThat(persisted.get("FOREST_HOTSPOT_01").getCenterYM()).isEqualTo(-67.89);
        assertThat(persisted.get("FOREST_HOTSPOT_01").getRadiusM()).isEqualTo(44.0);
        assertThat(persisted.get("FOREST_HOTSPOT_01").getTemperatureC()).isEqualTo(321.0);
    }

    @Test
    void repeatedThermalSeedDoesNotRecreateDeletedDatabaseRows() {
        seeder.seedThermalSources();
        persisted.remove("FOREST_HOTSPOT_02");

        seeder.seedThermalSources();

        assertThat(persisted).doesNotContainKey("FOREST_HOTSPOT_02");
        assertThat(persisted).hasSize(4);
    }

    @Test
    void forestThermalCentersAreInsideForestPolygon() {
        seeder.seedThermalSources();

        assertThat(persisted.values()).allSatisfy(source -> {
            Point center = SimulationZoneSeeder.GEOMETRY_FACTORY.createPoint(
                    new Coordinate(source.getCenterXM(), source.getCenterYM()));
            center.setSRID(SimulationZoneSeeder.SRID);
            assertThat(forest.getPolygon().covers(center)).isTrue();
        });
    }

    @Test
    void persistsExpectedTemperaturesTypesAndLocalCoordinateSystem() {
        seeder.seedThermalSources();

        assertThat(persisted.get("FOREST_AMBIENT").getTemperatureC()).isEqualTo(28.0);
        assertThat(persisted.get("FOREST_BURNT_GROUND_01").getTemperatureC()).isEqualTo(50.0);
        assertThat(persisted.get("FOREST_HOTSPOT_01").getThermalType()).isEqualTo(ThermalType.HOTSPOT);
        assertThat(persisted.get("FOREST_HOTSPOT_02").getTemperatureC()).isEqualTo(145.0);
        assertThat(persisted.get("FOREST_FIRE_CORE_01").getThermalType()).isEqualTo(ThermalType.FIRE);
        assertThat(persisted.get("FOREST_FIRE_CORE_01").getTemperatureC()).isEqualTo(250.0);
        assertThat(persisted.values()).allSatisfy(source -> {
            assertThat(source.getSourceWorld()).isEqualTo("forest_monitoring_compact");
            assertThat(source.getCoordinateSystem()).isEqualTo("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        });
    }

    @Test
    void thermalSeedDoesNotChangeZoneRestrictionsOrSrid() {
        SimulationZoneSeeder.SimulationZone airport = zoneSeed("AIRPORT");
        SimulationZoneSeeder.SimulationZone forestSeed = zoneSeed("FOREST_MONITORING_AREA");

        assertThat(airport.restricted()).isTrue();
        assertThat(forestSeed.restricted()).isFalse();
        assertThat(airport.polygon().getSRID()).isZero();
        assertThat(forestSeed.polygon().getSRID()).isZero();
    }

    private SimulationZoneSeeder.SimulationZone zoneSeed(String code) {
        return seeder.simulationZones().stream()
                .filter(zone -> zone.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private Zone toEntity(SimulationZoneSeeder.SimulationZone seed) {
        Zone zone = new Zone();
        zone.setCode(seed.code());
        zone.setName(seed.name());
        zone.setZoneType(seed.type());
        zone.setPurpose(seed.purpose());
        zone.setRestricted(seed.restricted());
        zone.setCenterXM(seed.x());
        zone.setCenterYM(seed.y());
        zone.setRadiusM(seed.radius());
        zone.setSourceWorld("forest_monitoring_compact");
        zone.setCoordinateSystem("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        zone.setPolygon(seed.polygon());
        return zone;
    }
}
