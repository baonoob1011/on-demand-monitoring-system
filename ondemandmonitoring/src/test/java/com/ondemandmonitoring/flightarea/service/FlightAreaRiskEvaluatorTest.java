package com.ondemandmonitoring.flightarea.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.AffectedZone;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.Assessment;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.FindingSeverity;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.FlightAreaRiskLevel;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.NearbyFeature;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.OsmContext;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.RestrictedZones;
import com.ondemandmonitoring.flightarea.service.ElevationService.ElevationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlightAreaRiskEvaluatorTest {

    private final FlightAreaRiskEvaluator evaluator = new FlightAreaRiskEvaluator(new FlightAreaProperties());

    private static final ElevationResult ELEVATION = new ElevationResult(true, 18.2, "AMSL", "test", null);
    private static final ElevationResult NO_ELEVATION = new ElevationResult(false, null, "AMSL", "test", "PROVIDER_UNAVAILABLE");
    private static final RestrictedZones CLEAR_ZONES = new RestrictedZones(true, false, false, 850.0, List.of());
    private static final OsmContext CLEAR_OSM = new OsmContext(true, null, 300.0, 0, 0, 0, 0, false, false, List.of());

    private static AffectedZone zone(String code, double distance, boolean intersects) {
        return new AffectedZone("id-" + code, code, "Zone " + code, "RESTRICTED", null, true, distance, intersects);
    }

    private static OsmContext osm(int buildings, int towers, boolean aerodrome, boolean helipad, List<NearbyFeature> features) {
        return new OsmContext(true, null, 300.0, buildings, towers, 0, 0, aerodrome, helipad, features);
    }

    private Assessment run(ElevationResult elevation, RestrictedZones zones, OsmContext osm, Double altitude) {
        return evaluator.evaluate(elevation, zones, osm, 300, altitude);
    }

    private static boolean has(Assessment assessment, String code) {
        return assessment.findings().stream().anyMatch(finding -> finding.code().equals(code));
    }

    @Test
    void aCompleteCleanPictureIsFavorable() {
        Assessment assessment = run(ELEVATION, CLEAR_ZONES, CLEAR_OSM, 60.0);

        assertEquals(FlightAreaRiskLevel.FAVORABLE, assessment.level());
        assertTrue(assessment.findings().isEmpty());
        assertEquals("Đánh giá sơ bộ. Điều kiện bay cuối cùng được xác nhận trong quá trình lập kế hoạch và pre-flight.",
                assessment.disclaimer());
    }

    @Test
    void anIntersectionWithARestrictedZoneIsHighRisk() {
        RestrictedZones zones = new RestrictedZones(true, false, true, 0.0, List.of(zone("ZONE-003", 120.0, true)));

        Assessment assessment = run(ELEVATION, zones, CLEAR_OSM, 60.0);

        assertEquals(FlightAreaRiskLevel.HIGH_RISK, assessment.level());
        var finding = assessment.findings().stream().filter(f -> f.code().equals("RESTRICTED_ZONE_INTERSECTION")).findFirst().orElseThrow();
        assertEquals(FindingSeverity.HIGH, finding.severity());
        assertTrue(finding.message().contains("ZONE-003"));
    }

    @Test
    void aPointInsideARestrictedZoneIsHighRisk() {
        RestrictedZones zones = new RestrictedZones(true, true, true, 0.0, List.of(zone("TSN", 0.0, true)));

        Assessment assessment = run(ELEVATION, zones, CLEAR_OSM, 60.0);

        assertEquals(FlightAreaRiskLevel.HIGH_RISK, assessment.level());
        assertTrue(has(assessment, "RESTRICTED_ZONE_INSIDE"));
    }

    @Test
    void aRestrictedZoneJustOutsideTheAreaNeedsReview() {
        RestrictedZones zones = new RestrictedZones(true, false, false, 450.0, List.of(zone("ZONE-9", 450.0, false)));

        Assessment assessment = run(ELEVATION, zones, CLEAR_OSM, 60.0);

        assertEquals(FlightAreaRiskLevel.NEEDS_REVIEW, assessment.level());
        var finding = assessment.findings().get(0);
        assertEquals("NEAR_RESTRICTED_ZONE", finding.code());
        assertEquals(FindingSeverity.WARNING, finding.severity());
        assertTrue(finding.message().contains("450"));
    }

    @Test
    void aFarRestrictedZoneDoesNotRaiseAnything() {
        RestrictedZones zones = new RestrictedZones(true, false, false, 9000.0, List.of());

        assertEquals(FlightAreaRiskLevel.FAVORABLE, run(ELEVATION, zones, CLEAR_OSM, 60.0).level());
    }

    @Test
    void missingElevationIsNeverFavorable() {
        Assessment assessment = run(NO_ELEVATION, CLEAR_ZONES, CLEAR_OSM, 60.0);

        assertEquals(FlightAreaRiskLevel.NEEDS_REVIEW, assessment.level());
        var finding = assessment.findings().get(0);
        assertEquals("ELEVATION_UNAVAILABLE", finding.code());
        assertEquals(FindingSeverity.INFO, finding.severity());
        assertEquals("Không thể lấy dữ liệu độ cao địa hình.", finding.message());
    }

    @Test
    void missingOpenStreetMapDataIsNeverFavorable() {
        OsmContext unavailable = new OsmContext(false, "PROVIDER_UNAVAILABLE", null, 0, 0, 0, 0, false, false, List.of());

        Assessment assessment = run(ELEVATION, CLEAR_ZONES, unavailable, 60.0);

        assertEquals(FlightAreaRiskLevel.NEEDS_REVIEW, assessment.level());
        assertTrue(has(assessment, "OSM_UNAVAILABLE"));
    }

    @Test
    void noGpsRestrictedZoneDataMeansTheAbsenceOfZonesProvesNothing() {
        RestrictedZones noData = new RestrictedZones(false, false, false, null, List.of());

        Assessment assessment = run(ELEVATION, noData, CLEAR_OSM, 60.0);

        assertEquals(FlightAreaRiskLevel.NEEDS_REVIEW, assessment.level());
        assertTrue(has(assessment, "RESTRICTED_ZONE_DATA_MISSING"));
    }

    @Test
    void aRestrictedZoneStillWinsOverMissingExternalData() {
        RestrictedZones zones = new RestrictedZones(true, true, true, 0.0, List.of(zone("TSN", 0.0, true)));

        assertEquals(FlightAreaRiskLevel.HIGH_RISK, run(NO_ELEVATION, zones,
                new OsmContext(false, "PROVIDER_TIMEOUT", null, 0, 0, 0, 0, false, false, List.of()), null).level());
    }

    @Test
    void anAerodromeOrHelipadNeedsReview() {
        Assessment aerodrome = run(ELEVATION, CLEAR_ZONES, osm(0, 0, true, false, List.of()), 60.0);
        Assessment helipad = run(ELEVATION, CLEAR_ZONES, osm(0, 0, false, true, List.of()), 60.0);

        assertEquals(FlightAreaRiskLevel.NEEDS_REVIEW, aerodrome.level());
        assertTrue(aerodrome.findings().get(0).message().contains("OpenStreetMap"));
        assertEquals("AERODROME_NEARBY", aerodrome.findings().get(0).code());
        assertEquals("HELIPAD_NEARBY", helipad.findings().get(0).code());
    }

    @Test
    void buildingsAndTowersAreContextNotRisk() {
        OsmContext crowded = osm(120, 2, false, false, List.of(
                new NearbyFeature("TOWER", null, 40.0, null, 10.0, 106.0, "OPENSTREETMAP")));

        Assessment assessment = run(ELEVATION, CLEAR_ZONES, crowded, 60.0);

        assertEquals(FlightAreaRiskLevel.FAVORABLE, assessment.level());
        assertTrue(has(assessment, "BUILDINGS_PRESENT"));
        assertTrue(has(assessment, "OBSTACLES_NEARBY"));
        assertTrue(assessment.findings().stream().allMatch(f -> f.severity() == FindingSeverity.INFO));
    }

    @Test
    void aKnownHeightNearTheRequestedAltitudeNeedsReview() {
        OsmContext tall = osm(1, 1, false, false, List.of(
                new NearbyFeature("TOWER", "Tháp", 80.0, 58.0, 10.0, 106.0, "OPENSTREETMAP")));

        assertEquals(FlightAreaRiskLevel.NEEDS_REVIEW, run(ELEVATION, CLEAR_ZONES, tall, 60.0).level());
        assertTrue(has(run(ELEVATION, CLEAR_ZONES, tall, 60.0), "STRUCTURE_NEAR_FLIGHT_ALTITUDE"));
        // Well below the requested altitude (beyond the margin), or no altitude given: no warning.
        assertFalse(has(run(ELEVATION, CLEAR_ZONES, tall, 100.0), "STRUCTURE_NEAR_FLIGHT_ALTITUDE"));
        assertFalse(has(run(ELEVATION, CLEAR_ZONES, tall, null), "STRUCTURE_NEAR_FLIGHT_ALTITUDE"));
    }

    @Test
    void aCappedOpenStreetMapRadiusIsDisclosed() {
        OsmContext capped = new OsmContext(true, null, 150.0, 0, 0, 0, 0, false, false, List.of());

        Assessment assessment = run(ELEVATION, CLEAR_ZONES, capped, 60.0);

        assertEquals(FlightAreaRiskLevel.FAVORABLE, assessment.level());
        assertTrue(has(assessment, "OSM_RADIUS_CAPPED"));
    }

    @Test
    void neverUsesSafeWording() {
        String text = run(ELEVATION, CLEAR_ZONES, CLEAR_OSM, 60.0).disclaimer().toLowerCase();
        assertFalse(text.contains("an toàn tuyệt đối"));
        assertFalse(text.contains("safe"));
    }
}
