package com.ondemandmonitoring.zone.config;

import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.domain.SimulationMapFeature;
import com.ondemandmonitoring.zone.repository.SimulationMapFeatureRepository;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.List;
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

    static final String SOURCE_WORLD = "forest_monitoring_compact";
    static final String COORDINATE_SYSTEM = "LOCAL_SIMULATION_METERS_GAZEBO_XY";
    static final int SRID = 0;
    static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), SRID);

    ZoneRepository zoneRepository;
    SimulationMapFeatureRepository simulationMapFeatureRepository;
    JdbcTemplate jdbcTemplate;
    Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        ensureRestrictedColumn();
        List<SimulationZone> zones = simulationZones();

        int createdZones = 0;
        if (zoneRepository.count() == 0) {
            for (SimulationZone zone : zones) {
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
        }

        if (createdZones > 0) {
            log.info("Seeded {} simulation zones from {}", createdZones, SOURCE_WORLD);
        }
        markAirportRestricted();

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

    private List<SimulationZone> simulationZones() {
        return List.of(
                new SimulationZone(
                        "DRONE_BASE",
                        "Helipad / Drone Base",
                        "HOME",
                        false,
                        -31.0,
                        -296.7,
                        68.0,
                        "Takeoff, landing, return-to-launch, operations staging",
                        rectangle(-105.0, -356.0, 43.0, -237.0)),
                new SimulationZone(
                        "AIRPORT",
                        "Airport",
                        "AIRPORT",
                        true,
                        -236.7,
                        -246.0,
                        110.0,
                        "Runway and aircraft operating area inspection",
                        polygon(
                                new Coordinate(-348.0, -368.0),
                                new Coordinate(-123.0, -368.0),
                                new Coordinate(-123.0, -125.0),
                                new Coordinate(-348.0, -125.0))),
                new SimulationZone(
                        "DAM",
                        "Dam",
                        "DAM",
                        false,
                        0.0,
                        149.5,
                        88.0,
                        "Dam wall, spillway and water discharge inspection",
                        polygon(
                                new Coordinate(-112.0, -32.0),
                                new Coordinate(112.0, -32.0),
                                new Coordinate(112.0, 330.0),
                                new Coordinate(72.0, 330.0),
                                new Coordinate(72.0, 95.0),
                                new Coordinate(-72.0, 95.0),
                                new Coordinate(-72.0, 330.0),
                                new Coordinate(-112.0, 330.0))),
                new SimulationZone(
                        "CONSTRUCTION_SITE",
                        "Construction Site",
                        "CONSTRUCTION",
                        false,
                        218.0,
                        -50.0,
                        58.0,
                        "Construction progress and restricted area inspection",
                        polygon(
                                new Coordinate(132.0, -112.0),
                                new Coordinate(298.0, -112.0),
                                new Coordinate(298.0, 12.0),
                                new Coordinate(132.0, 12.0))),
                new SimulationZone(
                        "AGRICULTURAL_FIELD",
                        "Agricultural Field",
                        "AGRICULTURE",
                        false,
                        268.0,
                        -203.0,
                        46.0,
                        "Crop health survey and dry-area detection",
                        polygon(
                                new Coordinate(214.0, -236.0),
                                new Coordinate(322.0, -236.0),
                                new Coordinate(322.0, -170.0),
                                new Coordinate(214.0, -170.0))),
                new SimulationZone(
                        "INDUSTRIAL_WAREHOUSE",
                        "Industrial Warehouse",
                        "INDUSTRIAL",
                        false,
                        -206.9,
                        -65.1,
                        54.0,
                        "Warehouse, tank, roof and yard monitoring",
                        polygon(
                                new Coordinate(-286.0, -114.0),
                                new Coordinate(-128.0, -114.0),
                                new Coordinate(-128.0, 4.0),
                                new Coordinate(-286.0, 4.0))),
                new SimulationZone(
                        "LOGISTICS_YARD",
                        "Logistics Yard",
                        "LOGISTICS",
                        false,
                        175.9,
                        -295.1,
                        88.0,
                        "Container, loading and storage yard monitoring",
                        polygon(
                                new Coordinate(84.0, -380.0),
                                new Coordinate(267.0, -380.0),
                                new Coordinate(267.0, -209.0),
                                new Coordinate(84.0, -209.0))),
                new SimulationZone(
                        "TELECOM_TOWER",
                        "Telecom Tower",
                        "TELECOM",
                        false,
                        260.0,
                        280.0,
                        26.0,
                        "Communication tower inspection",
                        octagon(260.0, 280.0, 26.0)),
                new SimulationZone(
                        "LANDSLIDE_FLOOD_AREA",
                        "Landslide / Flood Area",
                        "ENVIRONMENTAL_HAZARD",
                        false,
                        -259.3,
                        282.2,
                        72.0,
                        "Landslide, blocked trail and flood inspection",
                        polygon(
                                new Coordinate(-336.0, 222.0),
                                new Coordinate(-196.0, 222.0),
                                new Coordinate(-176.0, 338.0),
                                new Coordinate(-322.0, 338.0))),
                new SimulationZone(
                        "FOREST_MONITORING_AREA",
                        "Forest Monitoring Area",
                        "FOREST",
                        false,
                        -185.0,
                        38.0,
                        82.0,
                        "Forest survey, vegetation monitoring and search-area inspection",
                        polygon(
                                new Coordinate(-292.0, -82.0),
                                new Coordinate(-128.0, -96.0),
                                new Coordinate(-58.0, 18.0),
                                new Coordinate(-82.0, 138.0),
                                new Coordinate(-222.0, 168.0),
                                new Coordinate(-304.0, 62.0))),
                new SimulationZone(
                        "REMOTE_MONITORING_TARGET",
                        "Remote Monitoring Target",
                        "REMOTE_TARGET",
                        false,
                        250.0,
                        45.0,
                        42.0,
                        "Longer-distance waypoint target and orbit inspection",
                        octagon(250.0, 45.0, 42.0)));
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

    private record SimulationZone(
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
}
