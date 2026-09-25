package com.ondemandmonitoring.zone.config;

import com.ondemandmonitoring.environment.domain.AtmosphereProfile;
import com.ondemandmonitoring.environment.repository.AtmosphereProfileRepository;
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
    AtmosphereProfileRepository atmosphereProfileRepository;
    JdbcTemplate jdbcTemplate;
    Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        ensureRestrictedColumn();
        List<SimulationZone> zones = simulationZones();

        int createdZones = 0;
        int updatedZones = 0;
        for (SimulationZone zone : zones) {
            Optional<Zone> existing = zoneRepository.findByCode(zone.code());
            Zone entity = existing.orElseGet(Zone::new);
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
            if (existing.isPresent()) {
                updatedZones++;
            } else {
                createdZones++;
            }
        }

        log.info("Seeded simulation zones from {} (created={}, updated={})",
                SOURCE_WORLD, createdZones, updatedZones);
        markAirportRestricted();
        seedThermalSources();
        seedAtmosphereProfile();

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

    void seedAtmosphereProfile() {
        if (atmosphereProfileRepository.existsBySourceWorldAndActiveTrue(SOURCE_WORLD)) {
            log.info("Skipping atmosphere profile seed because {} already has an active profile", SOURCE_WORLD);
            return;
        }

        AtmosphereProfile profile = new AtmosphereProfile();
        profile.setSourceWorld(SOURCE_WORLD);
        profile.setBasePressurePa(101_325.0);
        profile.setBaseAltitudeM(0.0);
        profile.setActive(true);
        atmosphereProfileRepository.save(profile);
        log.info("Seeded default atmosphere profile for {} (basePressurePa={}, baseAltitudeM={})",
                SOURCE_WORLD, profile.getBasePressurePa(), profile.getBaseAltitudeM());
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

    private Polygon polygonExact(Coordinate... points) {
        Coordinate[] coordinates = new Coordinate[points.length + 1];
        for (int i = 0; i < points.length; i++) {
            coordinates[i] = new Coordinate(points[i].x, points[i].y);
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
                        "LOGISTICS_YARD",
                        "Logistics Yard",
                        "LOGISTICS",
                        false,
                        139.145693419814,
                        -321.3862851742284,
                        114.11964890958473,
                        "Container, loading and storage yard monitoring",
                        polygonExact(
                                new Coordinate(68.95623066280409, -388.330972884988),
                                new Coordinate(211.95623066280407, -381.230972884988),
                                new Coordinate(238.94770662852704, -286.9824155949244),
                                new Coordinate(57.366230662804085, -241.79097288498798))),
                new SimulationZone(
                        "DAM",
                        "Dam",
                        "DAM",
                        false,
                        12.519335019577102,
                        28.76067421310007,
                        291.70433026930954,
                        "Dam wall, spillway and water discharge inspection",
                        polygonExact(
                                new Coordinate(-61.7002342063322, -236.34594164407727),
                                new Coordinate(51.3562107367149, -260.3467684140278),
                                new Coordinate(135.4648931432244, 239.47498885276502),
                                new Coordinate(59.35454184073578, 265.2506955811425),
                                new Coordinate(28.194541840735774, 263.46069558114255),
                                new Coordinate(-30.025458159264225, 261.0106955811425),
                                new Coordinate(-63.37834564044442, 205.7039926457046),
                                new Coordinate(-91.2270513985826, 234.42134151454093))),
                new SimulationZone(
                        "FOREST_MONITORING_AREA",
                        "Forest Monitoring Area",
                        "FOREST",
                        false,
                        -153.07958631557224,
                        -5.74990159281607,
                        176.41741086945504,
                        "Forest survey, vegetation monitoring and search-area inspection",
                        polygonExact(
                                new Coordinate(-114.64592817410448, -66.79729515949634),
                                new Coordinate(-86.14678571391302, -54.43734152071602),
                                new Coordinate(-66.98237719045625, -38.527028554483934),
                                new Coordinate(-92.44074403389453, 142.2905073449026),
                                new Coordinate(-179.28678571391302, 40.852658479283974),
                                new Coordinate(-296.816785713913, -108.03734152071601))),
                new SimulationZone(
                        "AGRICULTURAL_FIELD",
                        "Agricultural Field",
                        "AGRICULTURE",
                        false,
                        189.22019372570196,
                        -217.91925413365598,
                        126.44561576835451,
                        "Crop health survey and dry-area detection",
                        polygonExact(
                                new Coordinate(64.7619773618294, -240.24963985719037),
                                new Coordinate(255.94144817148606, -294.0631097252151),
                                new Coordinate(288.36248911984484, -163.28784365646757),
                                new Coordinate(129.28248911984485, -159.94784365646754))),
                new SimulationZone(
                        "AIRPORT",
                        "Airport",
                        "AIRPORT",
                        true,
                        -191.25102446379535,
                        -282.98745476687617,
                        165.58532543676702,
                        "Runway and aircraft operating area inspection",
                        polygonExact(
                                new Coordinate(-303.75102446379543, -404.48745476687617),
                                new Coordinate(-78.7510244637952, -404.48745476687617),
                                new Coordinate(-78.7510244637952, -161.4874547668761),
                                new Coordinate(-303.75102446379543, -161.4874547668761))),
                new SimulationZone(
                        "INDUSTRIAL_WAREHOUSE",
                        "Industrial Warehouse",
                        "INDUSTRIAL",
                        false,
                        -143.23062371562537,
                        -114.81108969620317,
                        113.32913184143027,
                        "Warehouse, tank, roof and yard monitoring",
                        polygonExact(
                                new Coordinate(-187.36306346370972, -158.17978765074838),
                                new Coordinate(-78.09292524404549, -159.34636969449593),
                                new Coordinate(-67.98254258693515, -55.90919361220344),
                                new Coordinate(-256.1915857600335, -105.68348442789366))),
                new SimulationZone(
                        "CONSTRUCTION_SITE",
                        "Construction Site",
                        "CONSTRUCTION",
                        false,
                        194.28785918114278,
                        -98.75079262560652,
                        115.2411810661449,
                        "Construction progress and restricted area inspection",
                        polygonExact(
                                new Coordinate(104.0271568575314, -156.553660742234),
                                new Coordinate(299.4839317714034, -145.8073131631964),
                                new Coordinate(281.0241839984052, -45.29033996245829),
                                new Coordinate(98.95355904858229, -44.43152924084802))),
                new SimulationZone(
                        "LANDSLIDE_FLOOD_AREA",
                        "Landslide / Flood Area",
                        "ENVIRONMENTAL_HAZARD",
                        false,
                        -219.3346554204865,
                        100.63047130709104,
                        222.77629271229262,
                        "Landslide, blocked trail and flood inspection",
                        polygonExact(
                                new Coordinate(-300.8895268681381, 203.62155736914286),
                                new Coordinate(-301.6295268681381, -106.3884426308571),
                                new Coordinate(-109.08952686813811, 126.92155736914287),
                                new Coordinate(-80.29952686813812, 214.5815573691429))),
                new SimulationZone(
                        "TELECOM_TOWER",
                        "Telecom Tower",
                        "TELECOM",
                        false,
                        182.71799184259135,
                        110.65620524037989,
                        64.66197446625816,
                        "Communication tower inspection",
                        polygonExact(
                                new Coordinate(231.60551522410242, 149.08727480280152),
                                new Coordinate(183.3938203235917, 143.74187559672214),
                                new Coordinate(163.4938203235917, 143.74187559672214),
                                new Coordinate(149.4238203235917, 129.6718755967221),
                                new Coordinate(118.7838203235917, 100.98187559672209),
                                new Coordinate(150.2438203235917, 75.36187559672211),
                                new Coordinate(204.9638203235917, 76.48187559672212),
                                new Coordinate(232.3238203235917, 104.88187559672213))),
                new SimulationZone(
                        "REMOTE_MONITORING_TARGET",
                        "Remote Monitoring Target",
                        "REMOTE_TARGET",
                        false,
                        199.76439304467283,
                        18.025160134743047,
                        122.01357653407322,
                        "Longer-distance waypoint target and orbit inspection",
                        polygonExact(
                                new Coordinate(287.1689564234234, 60.86550243555595),
                                new Coordinate(264.4389564234234, 83.59550243555594),
                                new Coordinate(243.76394886361754, 100.57277901567107),
                                new Coordinate(119.87135205201912, 52.67668810716452),
                                new Coordinate(92.15463887754174, -39.48530557999237),
                                new Coordinate(303.68572402516327, -40.20722962673068),
                                new Coordinate(264.4389564234234, 5.995502435555963),
                                new Coordinate(287.1689564234234, 28.72550243555594))),
                new SimulationZone(
                        "DRONE_BASE",
                        "Helipad / Drone Base",
                        "HOME",
                        false,
                        -14.307585446249302,
                        -298.25494173650185,
                        82.49692575887347,
                        "Takeoff, landing, return-to-launch, operations staging",
                        polygonExact(
                                new Coordinate(-55.36315623932795, -361.9725239938444),
                                new Coordinate(36.82684376067205, -337.5325239938444),
                                new Coordinate(51.49684376067205, -263.0925239938444),
                                new Coordinate(-72.51315623932796, -239.79252399384438))));
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
