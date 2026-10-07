package com.ondemandmonitoring.flightarea.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ondemandmonitoring.flightarea.repository.RestrictedZoneSpatialRepository.ZoneDistance;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Opt-in: runs the repository's real SQL on a PostGIS database. It only creates a TEMPORARY table named
 * {@code zones}, which shadows the real table for this one connection and disappears with it, so no
 * stored data is read or changed. Set ODMS_FLIGHT_AREA_TEST_JDBC_URL (+ _USER / _PASSWORD) to enable.
 *
 * <p>Geometry: a 0.01 x 0.01 degree square near Tan Son Nhat. At latitude 10.8 one degree of longitude is
 * about 109.3 km, so 0.01 degree east of the square is ~1093 m - not 0.01 "units" as plain SRID-0 maths would say.
 */
@EnabledIfEnvironmentVariable(named = "ODMS_FLIGHT_AREA_TEST_JDBC_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestrictedZoneSpatialRepositoryPostgresTest {

    private static final double LAT = 10.8;
    private static final double WEST = 106.60;
    private static final double EAST = 106.61;

    private Connection connection;
    private RestrictedZoneSpatialRepository repository;
    private JdbcTemplate jdbc;

    @BeforeAll
    void connect() throws SQLException {
        connection = DriverManager.getConnection(
                System.getenv("ODMS_FLIGHT_AREA_TEST_JDBC_URL"),
                System.getenv().getOrDefault("ODMS_FLIGHT_AREA_TEST_JDBC_USER", "postgres"),
                System.getenv().getOrDefault("ODMS_FLIGHT_AREA_TEST_JDBC_PASSWORD", ""));
        DataSource single = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(single);
        repository = new RestrictedZoneSpatialRepository(new NamedParameterJdbcTemplate(single));
        jdbc.execute("CREATE TEMP TABLE zones ("
                + "id varchar(36), code varchar(80), name varchar(120), zone_type varchar(80), purpose varchar(500), "
                + "restricted boolean, coordinate_system varchar(120), polygon geometry(Polygon,0))");
    }

    @AfterAll
    void close() throws SQLException {
        connection.close();
    }

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM pg_temp.zones");
        insert("gps-1", "TSN_TEST", true, "WGS84_GPS_LON_LAT", square(WEST, EAST, 10.79, 10.81));
        // Same idea but local simulation metres: must never take part, even though restricted = true
        // and its raw numbers would "contain" a lon/lat-looking point in a degrees-versus-metres mix-up.
        insert("sim-1", "SIM_AIRPORT", true, "LOCAL_SIMULATION_METERS_GAZEBO_XY", square(100, 200, 10, 12));
        // A GPS zone that is not restricted is ignored too.
        insert("gps-2", "GPS_FREE", false, "WGS84_GPS_LON_LAT", square(106.70, 106.71, 10.79, 10.81));
    }

    private void insert(String id, String code, boolean restricted, String system, String wkt) {
        jdbc.update("INSERT INTO pg_temp.zones VALUES (?,?,?,?,?,?,?, ST_GeomFromText(?, 0))",
                id, code, "Zone " + code, "RESTRICTED", "test", restricted, system, wkt);
    }

    private static String square(double west, double east, double south, double north) {
        return String.format(java.util.Locale.ROOT, "POLYGON((%f %f,%f %f,%f %f,%f %f,%f %f))",
                west, south, east, south, east, north, west, north, west, south);
    }

    @Test
    void aPointInsideTheZoneIsInsideWithZeroDistance() {
        List<ZoneDistance> rows = repository.findRestrictedZonesWithin(LAT, 106.605, 1000);

        assertEquals(1, rows.size());
        assertEquals("TSN_TEST", rows.get(0).code());
        assertTrue(rows.get(0).pointInside());
        assertEquals(0.0, rows.get(0).distanceMeters(), 0.001);
    }

    @Test
    void aPointOutsideReportsTrueMetresNotDegrees() {
        // 0.01 degree of longitude east of the square at latitude 10.8.
        List<ZoneDistance> rows = repository.findRestrictedZonesWithin(LAT, EAST + 0.01, 5000);

        assertEquals(1, rows.size());
        assertFalse(rows.get(0).pointInside());
        assertEquals(1093.0, rows.get(0).distanceMeters(), 15.0);
    }

    @Test
    void aZoneBeyondTheSearchRadiusIsNotListedButStillBoundsTheNearestDistance() {
        double lon = EAST + 0.01;

        assertTrue(repository.findRestrictedZonesWithin(LAT, lon, 800).isEmpty());
        Double nearest = repository.findNearestRestrictedDistance(LAT, lon);
        assertNotNull(nearest);
        assertEquals(1093.0, nearest, 15.0);
    }

    @Test
    void theSearchRadiusIsAGeographicDistanceInMetres() {
        double lon = EAST + 0.01;

        assertEquals(1, repository.findRestrictedZonesWithin(LAT, lon, 1200).size());
        assertEquals(0, repository.findRestrictedZonesWithin(LAT, lon, 1000).size());
    }

    @Test
    void simulationMetreZonesAndNonRestrictedZonesAreIgnored() {
        // Inside the (non-restricted) free zone and right in the middle of the numbers of the simulation zone.
        assertTrue(repository.findRestrictedZonesWithin(LAT, 106.705, 1000).isEmpty());
        Double nearest = repository.findNearestRestrictedDistance(LAT, 106.705);
        assertNotNull(nearest);
        assertTrue(nearest > 8000, "only TSN_TEST counts: " + nearest);
    }

    @Test
    void nearestIsNullWhenThereIsNoGpsRestrictedZone() {
        jdbc.execute("DELETE FROM pg_temp.zones WHERE code = 'TSN_TEST'");

        assertNull(repository.findNearestRestrictedDistance(LAT, 106.6));
        assertTrue(repository.findRestrictedZonesWithin(LAT, 106.6, 1000).isEmpty());
    }

    @Test
    void zonesComeBackNearestFirst() {
        insert("gps-3", "SECOND", true, "WGS84_GPS_LON_LAT", square(106.62, 106.63, 10.79, 10.81));

        List<ZoneDistance> rows = repository.findRestrictedZonesWithin(LAT, 106.6175, 5000);

        assertEquals(List.of("SECOND", "TSN_TEST"), rows.stream().map(ZoneDistance::code).toList());
    }
}
