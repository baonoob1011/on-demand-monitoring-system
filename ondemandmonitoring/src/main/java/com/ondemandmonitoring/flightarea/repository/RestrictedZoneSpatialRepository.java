package com.ondemandmonitoring.flightarea.repository;

import com.ondemandmonitoring.zone.config.SimulationZoneSeeder;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostGIS queries over the existing {@code zones} table.
 *
 * <p>Coordinate systems: {@code zones.polygon} is {@code geometry(Polygon,0)} and the table mixes two
 * kinds of rows - Gazebo simulation zones in local metres, and real zones in WGS84 lon/lat
 * ({@code coordinate_system = 'WGS84_GPS_LON_LAT'}). Only the latter can be compared with a customer's
 * latitude/longitude, so every query filters on it. The polygon is tagged EPSG:4326 on the fly and
 * cast to {@code geography}, so distances are true metres on the spheroid, never degrees.
 */
@Repository
@RequiredArgsConstructor
public class RestrictedZoneSpatialRepository {

    public record ZoneDistance(
            String id,
            String code,
            String name,
            String zoneType,
            String purpose,
            boolean pointInside,
            double distanceMeters) {
    }

    private static final String ZONE_GEOGRAPHY = "ST_SetSRID(z.polygon, 4326)";
    private static final String POINT = "ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)";

    /** Restricted GPS zones whose true distance to the point is within the search radius, nearest first. */
    static final String ZONES_WITHIN_SQL = """
            SELECT z.id, z.code, z.name, z.zone_type, z.purpose,
                   ST_Covers(%1$s, %2$s) AS point_inside,
                   ST_Distance(%1$s::geography, %2$s::geography) AS distance_m
            FROM zones z
            WHERE z.restricted = TRUE
              AND z.coordinate_system = :coordinateSystem
              AND ST_DWithin(%1$s::geography, %2$s::geography, :searchRadiusMeters)
            ORDER BY distance_m, z.code
            """.formatted(ZONE_GEOGRAPHY, POINT);

    /** Distance to the nearest restricted GPS zone; NULL when the system has no such zone at all. */
    static final String NEAREST_DISTANCE_SQL = """
            SELECT MIN(ST_Distance(%1$s::geography, %2$s::geography)) AS distance_m
            FROM zones z
            WHERE z.restricted = TRUE
              AND z.coordinate_system = :coordinateSystem
            """.formatted(ZONE_GEOGRAPHY, POINT);

    private final NamedParameterJdbcTemplate jdbc;

    public List<ZoneDistance> findRestrictedZonesWithin(double latitude, double longitude, double searchRadiusMeters) {
        return jdbc.query(ZONES_WITHIN_SQL, params(latitude, longitude)
                        .addValue("searchRadiusMeters", searchRadiusMeters),
                (rs, rowNumber) -> new ZoneDistance(
                        rs.getString("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("zone_type"),
                        rs.getString("purpose"),
                        rs.getBoolean("point_inside"),
                        rs.getDouble("distance_m")));
    }

    /** Null when there is no restricted GPS zone to compare against. */
    public Double findNearestRestrictedDistance(double latitude, double longitude) {
        return jdbc.query(NEAREST_DISTANCE_SQL, params(latitude, longitude), rs -> {
            if (!rs.next()) return null;
            double value = rs.getDouble("distance_m");
            return rs.wasNull() ? null : value;
        });
    }

    private static MapSqlParameterSource params(double latitude, double longitude) {
        return new MapSqlParameterSource(Map.of(
                "latitude", latitude,
                "longitude", longitude,
                "coordinateSystem", SimulationZoneSeeder.GPS_COORDINATE_SYSTEM));
    }
}
