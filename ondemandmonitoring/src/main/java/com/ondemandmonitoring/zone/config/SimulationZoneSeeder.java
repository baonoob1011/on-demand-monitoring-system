package com.ondemandmonitoring.zone.config;

import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.domain.SimulationMapFeature;
import com.ondemandmonitoring.zone.domain.ThermalSource;
import com.ondemandmonitoring.zone.enums.ThermalType;
import com.ondemandmonitoring.zone.repository.SimulationMapFeatureRepository;
import com.ondemandmonitoring.zone.repository.ThermalSourceRepository;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SimulationZoneSeeder implements CommandLineRunner {

    public static final String SOURCE_WORLD = "forest_monitoring_compact";
    public static final String COORDINATE_SYSTEM = "LOCAL_SIMULATION_METERS_GAZEBO_XY";
    static final int SRID = 0;
    static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), SRID);

    ZoneRepository zoneRepository;
    SimulationMapFeatureRepository simulationMapFeatureRepository;
    ThermalSourceRepository thermalSourceRepository;
    JdbcTemplate jdbcTemplate;
    Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        ensureRestrictedColumn();
        List<SimulationZone> zones = simulationZones();

        int createdZones = 0;
        for (SimulationZone zone : zones) {
            if (zoneRepository.findByCode(zone.code()).isPresent()) {
                continue;
            }

            Zone entity = new Zone();
            entity.setCode(zone.code());
            entity.setName(zone.name());
            entity.setZoneType(zone.type());
            entity.setPurpose(zone.purpose());
            entity.setRestricted(zone.restricted());
            entity.setCenterXM(zone.x());
            entity.setCenterYM(zone.y());
            entity.setRadiusM(zone.radius());
            entity.setSourceWorld(SOURCE_WORLD);
            entity.setCoordinateSystem(COORDINATE_SYSTEM);
            entity.setPolygon(zone.polygon());
            zoneRepository.save(entity);
            createdZones++;
        }

        log.info("Seeded missing simulation zones from {} (created={}, existing={})",
                SOURCE_WORLD, createdZones, zones.size() - createdZones);
        markAirportRestricted();
        seedThermalSources();

        int createdMapFeatures = 0;
        for (MapFeature feature : simulationMapFeatures()) {
            boolean creating = simulationMapFeatureRepository.findByCode(feature.code()).isEmpty();
            SimulationMapFeature entity = simulationMapFeatureRepository.findByCode(feature.code())
                    .orElseGet(SimulationMapFeature::new);
            entity.setCode(feature.code());
            entity.setName(feature.name());
            entity.setFeatureType(feature.type());
            entity.setDisplayOrder(feature.displayOrder());
            entity.setSourceWorld(SOURCE_WORLD);
            entity.setCoordinateSystem(COORDINATE_SYSTEM);
            entity.setGeometry(feature.geometry());
            simulationMapFeatureRepository.save(entity);
            if (creating) {
                createdMapFeatures++;
            }
        }

        if (createdMapFeatures > 0) {
            log.info("Seeded {} simulation map features from {}", createdMapFeatures, SOURCE_WORLD);
        }

        createPgAdminGeometryViewerViews();
        logSimulationViewerUrls();
    }

    void seedThermalSources() {
        long existingThermalCount = thermalSourceRepository.count();
        if (existingThermalCount > 0) {
            log.info("Skipping simulation thermal seed because DB already has {} thermal sources", existingThermalCount);
            return;
        }

        int created = 0;
        for (ThermalSeed seed : thermalSources()) {
            Zone zone = zoneRepository.findByCode(seed.zoneCode()).orElse(null);
            if (zone == null) {
                log.warn("Skipping thermal source {} because zone {} does not exist", seed.code(), seed.zoneCode());
                continue;
            }

            ThermalSource source = new ThermalSource();
            source.setZone(zone);
            source.setCode(seed.code());
            source.setName(seed.name());
            source.setThermalType(seed.type());
            source.setTemperatureC(seed.temperatureC());
            source.setCenterXM(seed.x());
            source.setCenterYM(seed.y());
            source.setRadiusM(seed.radius());
            source.setActive(seed.active());
            source.setSourceWorld(SOURCE_WORLD);
            source.setCoordinateSystem(COORDINATE_SYSTEM);
            thermalSourceRepository.save(source);
            created++;
        }

        log.info("Seeded missing simulation thermal sources from {} (created={})", SOURCE_WORLD, created);
    }

    List<ThermalSeed> thermalSources() {
        return List.of(
                new ThermalSeed(
                        "FOREST_AMBIENT",
                        "Forest Ambient",
                        "FOREST_MONITORING_AREA",
                        ThermalType.AMBIENT,
                        28.0,
                        -200.0,
                        93.0,
                        42.0,
                        true),
                new ThermalSeed(
                        "FOREST_BURNT_GROUND_01",
                        "Forest Burnt Ground",
                        "FOREST_MONITORING_AREA",
                        ThermalType.WARM_AREA,
                        50.0,
                        -210.0,
                        150.0,
                        18.0,
                        true),
                new ThermalSeed(
                        "FOREST_HOTSPOT_01",
                        "Forest Hotspot 01",
                        "FOREST_MONITORING_AREA",
                        ThermalType.HOTSPOT,
                        110.0,
                        -178.0,
                        120.0,
                        5.0,
                        true),
                new ThermalSeed(
                        "FOREST_HOTSPOT_02",
                        "Forest Hotspot 02",
                        "FOREST_MONITORING_AREA",
                        ThermalType.HOTSPOT,
                        145.0,
                        -160.0,
                        80.0,
                        4.0,
                        true),
                new ThermalSeed(
                        "FOREST_FIRE_CORE_01",
                        "Forest Fire Core 01",
                        "FOREST_MONITORING_AREA",
                        ThermalType.FIRE,
                        250.0,
                        -185.0,
                        95.0,
                        2.0,
                        true));
    }

    private void logSimulationViewerUrls() {
        String port = environment.getProperty(
                "local.server.port",
                environment.getProperty("server.port", "8080"));

        log.info("==================================================");
        log.info("Simulation Geometry Viewer:");
        log.info("  http://127.0.0.1:{}/simulation-viewer/index.html", port);
        log.info("  http://localhost:{}/simulation-viewer/index.html", port);
        log.info("pgAdmin Geometry Viewer SQL:");
        log.info("  SELECT * FROM public.simulation_pgadmin_map ORDER BY layer, display_order, code;");
        log.info("==================================================");
    }

    private void ensureRestrictedColumn() {
        jdbcTemplate.execute("""
                ALTER TABLE IF EXISTS public.zones
                ADD COLUMN IF NOT EXISTS restricted BOOLEAN NOT NULL DEFAULT FALSE
                """);
    }

    private void markAirportRestricted() {
        jdbcTemplate.update("""
                UPDATE public.zones
                SET restricted = TRUE
                WHERE code = 'AIRPORT'
                """);
    }

    private void createPgAdminGeometryViewerViews() {
        jdbcTemplate.execute("DROP VIEW IF EXISTS public.simulation_pgadmin_map");
        jdbcTemplate.execute("DROP VIEW IF EXISTS public.zones_pgadmin_map");
        jdbcTemplate.execute("DROP VIEW IF EXISTS public.thermal_sources_wgs84");
        jdbcTemplate.execute("DROP VIEW IF EXISTS public.simulation_map_features_wgs84");
        jdbcTemplate.execute("DROP VIEW IF EXISTS public.zones_wgs84");

        jdbcTemplate.execute("""
                CREATE OR REPLACE VIEW public.zones_wgs84 AS
                SELECT
                    id,
                    code,
                    name,
                    zone_type,
                    purpose,
                    restricted,
                    center_x_m,
                    center_y_m,
                    radius_m,
                    source_world,
                    coordinate_system,
                    ST_SetSRID(
                        ST_Translate(
                            ST_Scale(polygon, 0.000009236, 0.000008983),
                            106.660172,
                            10.762622
                        ),
                        4326
                    )::geometry(Polygon,4326) AS geometry
                FROM public.zones
                """);

        jdbcTemplate.execute("""
                CREATE OR REPLACE VIEW public.simulation_map_features_wgs84 AS
                SELECT
                    id,
                    code,
                    name,
                    feature_type,
                    display_order,
                    source_world,
                    coordinate_system,
                    ST_SetSRID(
                        ST_Translate(
                            ST_Scale(geometry, 0.000009236, 0.000008983),
                            106.660172,
                            10.762622
                        ),
                        4326
                    )::geometry(Geometry,4326) AS geometry
                FROM public.simulation_map_features
                """);

        jdbcTemplate.execute("""
                CREATE OR REPLACE VIEW public.thermal_sources_wgs84 AS
                SELECT
                    id,
                    code,
                    name,
                    zone_id,
                    thermal_type,
                    temperature_c,
                    center_x_m,
                    center_y_m,
                    radius_m,
                    active,
                    source_world,
                    coordinate_system,
                    ST_SetSRID(
                        ST_Translate(
                            ST_Scale(
                                ST_Buffer(ST_SetSRID(ST_MakePoint(center_x_m, center_y_m), 0), radius_m),
                                0.000009236,
                                0.000008983
                            ),
                            106.660172,
                            10.762622
                        ),
                        4326
                    )::geometry(Polygon,4326) AS geometry
                FROM public.thermal_sources
                """);

        jdbcTemplate.execute("""
                CREATE OR REPLACE VIEW public.zones_pgadmin_map AS
                SELECT
                    id,
                    code,
                    name,
                    zone_type,
                    purpose,
                    restricted,
                    center_x_m,
                    center_y_m,
                    radius_m,
                    source_world,
                    coordinate_system,
                    geometry AS geom
                FROM public.zones_wgs84
                """);

        jdbcTemplate.execute("""
                CREATE OR REPLACE VIEW public.simulation_pgadmin_map AS
                SELECT
                    id,
                    code,
                    name,
                    'ZONE' AS layer,
                    zone_type AS feature_type,
                    restricted,
                    1000 AS display_order,
                    source_world,
                    coordinate_system,
                    geometry::geometry(Geometry,4326) AS geom
                FROM public.zones_wgs84
                UNION ALL
                SELECT
                    id,
                    code,
                    name,
                    'MAP' AS layer,
                    feature_type,
                    false AS restricted,
                    display_order,
                    source_world,
                    coordinate_system,
                    geometry::geometry(Geometry,4326) AS geom
                FROM public.simulation_map_features_wgs84
                UNION ALL
                SELECT
                    id,
                    code,
                    name,
                    'THERMAL' AS layer,
                    thermal_type AS feature_type,
                    false AS restricted,
                    2000 AS display_order,
                    source_world,
                    coordinate_system,
                    geometry::geometry(Geometry,4326) AS geom
                FROM public.thermal_sources_wgs84
                """);
    }

    private Polygon octagon(double centerX, double centerY, double radius) {
        Coordinate[] coordinates = new Coordinate[9];
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 8.0 + (i * Math.PI / 4.0);
            coordinates[i] = new Coordinate(
                    round(centerX + radius * Math.cos(angle)),
                    round(centerY + radius * Math.sin(angle)));
        }
        coordinates[8] = new Coordinate(coordinates[0]);

        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(coordinates);
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(shell);
        polygon.setSRID(SRID);
        return polygon;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Polygon rectangle(double minX, double minY, double maxX, double maxY) {
        return polygon(
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY));
    }

    private Polygon polygon(Coordinate... points) {
        Coordinate[] coordinates = new Coordinate[points.length + 1];
        for (int i = 0; i < points.length; i++) {
            coordinates[i] = new Coordinate(round(points[i].x), round(points[i].y));
        }
        coordinates[points.length] = new Coordinate(coordinates[0]);

        Polygon polygon = GEOMETRY_FACTORY.createPolygon(GEOMETRY_FACTORY.createLinearRing(coordinates));
        polygon.setSRID(SRID);
        return polygon;
    }

    private LineString line(Coordinate... points) {
        Coordinate[] coordinates = new Coordinate[points.length];
        for (int i = 0; i < points.length; i++) {
            coordinates[i] = new Coordinate(round(points[i].x), round(points[i].y));
        }

        LineString lineString = GEOMETRY_FACTORY.createLineString(coordinates);
        lineString.setSRID(SRID);
        return lineString;
    }

    List<SimulationZone> simulationZones() {
        return List.of(
                new SimulationZone(
                        "DRONE_BASE",
                        "Helipad / Drone Base",
                        "HOME",
                        false,
                        -22.801571199988913,
                        -273.47150118759254,
                        82.49875532933,
                        "Takeoff, landing, return-to-launch, operations staging",
                        polygon(
                                new Coordinate(-63.86, -337.19),
                                new Coordinate(28.33, -312.75),
                                new Coordinate(43.00, -238.31),
                                new Coordinate(-81.01, -215.01))),
                new SimulationZone(
                        "AIRPORT",
                        "Airport",
                        "AIRPORT",
                        true,
                        -201.12662040136175,
                        -245.2981690787902,
                        165.58532543676694,
                        "Runway and aircraft operating area inspection",
                        polygon(
                                new Coordinate(-313.63, -366.80),
                                new Coordinate(-88.63, -366.80),
                                new Coordinate(-88.63, -123.80),
                                new Coordinate(-313.63, -123.80))),
                new SimulationZone(
                        "DAM",
                        "Dam",
                        "DAM",
                        false,
                        10.332541095611454,
                        299.89813291970745,
                        177.88706685847177,
                        "Dam wall, spillway and water discharge inspection",
                        polygon(
                                new Coordinate(-46.85, 152.57),
                                new Coordinate(88.66, 160.96),
                                new Coordinate(149.54, 397.19),
                                new Coordinate(71.59, 417.89),
                                new Coordinate(40.43, 416.10),
                                new Coordinate(-17.79, 413.65),
                                new Coordinate(-72.41, 417.89),
                                new Coordinate(-140.13, 394.79))),
                new SimulationZone(
                        "CONSTRUCTION_SITE",
                        "Construction Site",
                        "CONSTRUCTION",
                        false,
                        181.08429258099966,
                        -48.57152098713513,
                        81.50111952863544,
                        "Construction progress and restricted area inspection",
                        polygon(
                                new Coordinate(132.00, -113.63),
                                new Coordinate(232.21, -92.63),
                                new Coordinate(238.24, -0.29),
                                new Coordinate(132.00, 10.37))),
                new SimulationZone(
                        "AGRICULTURAL_FIELD",
                        "Agricultural Field",
                        "AGRICULTURE",
                        false,
                        214.3248698788421,
                        -172.621925726457,
                        101.12841417775034,
                        "Crop health survey and dry-area detection",
                        polygon(
                                new Coordinate(128.78, -215.17),
                                new Coordinate(273.15, -229.37),
                                new Coordinate(303.94, -125.76),
                                new Coordinate(144.86, -122.42))),
                new SimulationZone(
                        "INDUSTRIAL_WAREHOUSE",
                        "Industrial Warehouse",
                        "INDUSTRIAL",
                        false,
                        -171.3394256943739,
                        -60.8064074628538,
                        89.62293078564005,
                        "Warehouse, tank, roof and yard monitoring",
                        polygon(
                                new Coordinate(-234.28, -115.36),
                                new Coordinate(-107.49, -123.70),
                                new Coordinate(-113.83, -5.78),
                                new Coordinate(-228.43, 5.53))),
                new SimulationZone(
                        "LOGISTICS_YARD",
                        "Logistics Yard",
                        "LOGISTICS",
                        false,
                        142.76432355969126,
                        -285.09449405832765,
                        116.52158569083771,
                        "Container, loading and storage yard monitoring",
                        polygon(
                                new Coordinate(67.54, -359.30),
                                new Coordinate(210.54, -352.20),
                                new Coordinate(245.10, -229.37),
                                new Coordinate(55.95, -212.76))),
                new SimulationZone(
                        "TELECOM_TOWER",
                        "Telecom Tower",
                        "TELECOM",
                        false,
                        202.05069075251456,
                        270.40852675750807,
                        57.097851942937574,
                        "Communication tower inspection",
                        polygon(
                                new Coordinate(224.28, 294.40),
                                new Coordinate(210.21, 308.47),
                                new Coordinate(190.31, 308.47),
                                new Coordinate(176.24, 294.40),
                                new Coordinate(145.60, 265.71),
                                new Coordinate(177.06, 240.09),
                                new Coordinate(231.78, 241.21),
                                new Coordinate(259.14, 269.61))),
                new SimulationZone(
                        "LANDSLIDE_FLOOD_AREA",
                        "Landslide / Flood Area",
                        "ENVIRONMENTAL_HAZARD",
                        false,
                        -244.70538550726846,
                        257.7900376484977,
                        222.7743598873677,
                        "Landslide, blocked trail and flood inspection",
                        polygon(
                                new Coordinate(-326.26, 360.78),
                                new Coordinate(-327.00, 50.77),
                                new Coordinate(-134.46, 284.08),
                                new Coordinate(-105.67, 371.74))),
                new SimulationZone(
                        "FOREST_MONITORING_AREA",
                        "Forest Monitoring Area",
                        "FOREST",
                        false,
                        -200.152292424777,
                        92.7099200393152,
                        214.002215488247,
                        "Forest survey, vegetation monitoring and search-area inspection",
                        polygon(
                                new Coordinate(-115.78, 2.55),
                                new Coordinate(-113.83, 91.88),
                                new Coordinate(-187.95, 135.36),
                                new Coordinate(-122.12, 291.98),
                                new Coordinate(-206.97, 187.17),
                                new Coordinate(-324.50, 38.28))),
                new SimulationZone(
                        "REMOTE_MONITORING_TARGET",
                        "Remote Monitoring Target",
                        "REMOTE_TARGET",
                        false,
                        191.7830168189289,
                        103.83556383076377,
                        41.99624864199183,
                        "Longer-distance waypoint target and orbit inspection",
                        polygon(
                                new Coordinate(230.58, 119.91),
                                new Coordinate(207.85, 142.64),
                                new Coordinate(175.71, 142.64),
                                new Coordinate(152.98, 119.91),
                                new Coordinate(152.98, 87.77),
                                new Coordinate(175.71, 65.04),
                                new Coordinate(207.85, 65.04),
                                new Coordinate(230.58, 87.77))));
    }

    private List<MapFeature> simulationMapFeatures() {
        return List.of(
                new MapFeature(
                        "MAP_BOUNDARY",
                        "Compact Simulation Boundary",
                        "BOUNDARY",
                        1,
                        rectangle(-452.0, -415.0, 450.0, 686.497)),
                new MapFeature(
                        "RIVER_CORRIDOR",
                        "Central River Corridor",
                        "WATER",
                        2,
                        polygon(
                                new Coordinate(-44.0, -392.0),
                                new Coordinate(-62.0, -250.0),
                                new Coordinate(-38.0, -95.0),
                                new Coordinate(-22.0, 58.0),
                                new Coordinate(-40.0, 210.0),
                                new Coordinate(-32.0, 318.0),
                                new Coordinate(44.0, 318.0),
                                new Coordinate(52.0, 210.0),
                                new Coordinate(32.0, 58.0),
                                new Coordinate(46.0, -95.0),
                                new Coordinate(22.0, -250.0),
                                new Coordinate(18.0, -392.0))),
                new MapFeature(
                        "MAIN_ROAD",
                        "Main Road",
                        "ROAD",
                        3,
                        line(
                                new Coordinate(-334.0, -252.0),
                                new Coordinate(-220.0, -110.0),
                                new Coordinate(-85.0, -62.0),
                                new Coordinate(0.0, -22.0),
                                new Coordinate(128.0, -40.0),
                                new Coordinate(230.0, 20.0),
                                new Coordinate(330.0, 20.0))),
                new MapFeature(
                        "NORTH_SERVICE_ROAD",
                        "North Service Road",
                        "ROAD",
                        4,
                        line(
                                new Coordinate(-320.0, 222.0),
                                new Coordinate(-260.0, 220.0),
                                new Coordinate(-148.0, 152.0),
                                new Coordinate(-58.0, 120.0),
                                new Coordinate(0.0, 230.0),
                                new Coordinate(112.0, 248.0),
                                new Coordinate(260.0, 230.0))),
                new MapFeature(
                        "SOUTH_SERVICE_ROAD",
                        "South Service Road",
                        "ROAD",
                        5,
                        line(
                                new Coordinate(-334.0, -318.0),
                                new Coordinate(-200.0, -310.0),
                                new Coordinate(-60.0, -312.0),
                                new Coordinate(0.0, -280.0),
                                new Coordinate(126.0, -295.0),
                                new Coordinate(176.0, -235.0),
                                new Coordinate(280.0, -202.0))),
                new MapFeature(
                        "DRONE_LAUNCH_PAD",
                        "Drone Launch Pad",
                        "LANDING_PAD",
                        6,
                        rectangle(-40.0, -320.0, 40.0, -240.0)),
                new MapFeature(
                        "CONSTRUCTION_BLOCK",
                        "Construction Block",
                        "WORK_AREA",
                        7,
                        rectangle(132.0, -112.0, 298.0, 12.0)),
                new MapFeature(
                        "INDUSTRIAL_BLOCK",
                        "Industrial Warehouse Block",
                        "WORK_AREA",
                        8,
                        rectangle(-286.0, -114.0, -128.0, 4.0)),
                new MapFeature(
                        "FOREST_BLOCK",
                        "Forest Block",
                        "FOREST",
                        9,
                        polygon(
                                new Coordinate(-292.0, -82.0),
                                new Coordinate(-128.0, -96.0),
                                new Coordinate(-58.0, 18.0),
                                new Coordinate(-82.0, 138.0),
                                new Coordinate(-222.0, 168.0),
                                new Coordinate(-304.0, 62.0))),
                new MapFeature(
                        "REMOTE_TARGET_BLOCK",
                        "Remote Target Block",
                        "TARGET",
                        10,
                        rectangle(210.0, 5.0, 292.0, 87.0)));
    }

    record SimulationZone(
            String code,
            String name,
            String type,
            boolean restricted,
            double x,
            double y,
            double radius,
            String purpose,
            Polygon polygon) {
    }

    private record MapFeature(
            String code,
            String name,
            String type,
            int displayOrder,
            org.locationtech.jts.geom.Geometry geometry) {
    }

    record ThermalSeed(
            String code,
            String name,
            String zoneCode,
            ThermalType type,
            double temperatureC,
            double x,
            double y,
            double radius,
            boolean active) {
    }
}
