package com.ondemandmonitoring.flightarea.service;

import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.AffectedZone;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.Assessment;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.AssessmentFinding;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.FindingSeverity;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.FlightAreaRiskLevel;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.NearbyFeature;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.OsmContext;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.RestrictedZones;
import com.ondemandmonitoring.flightarea.service.ElevationService.ElevationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Deterministic, rule-based evaluation. No AI/LLM is involved and no legal limit is encoded here;
 * thresholds come from {@link FlightAreaProperties}.
 *
 * <p>Levels: any HIGH finding gives HIGH_RISK; otherwise a WARNING, or missing data from any source,
 * gives NEEDS_REVIEW; only a complete, clean picture gives FAVORABLE. Buildings and towers are context
 * for planning and never raise the level on their own.
 */
@Component
@RequiredArgsConstructor
public class FlightAreaRiskEvaluator {

    public static final String DISCLAIMER =
            "Đánh giá sơ bộ. Điều kiện bay cuối cùng được xác nhận trong quá trình lập kế hoạch và pre-flight.";

    private final FlightAreaProperties properties;

    public Assessment evaluate(
            ElevationResult elevation,
            RestrictedZones zones,
            OsmContext osm,
            double radiusMeters,
            Double requestedAltitudeAglMeters) {
        List<AssessmentFinding> findings = new ArrayList<>();
        boolean dataMissing = false;

        // 1. Restricted zones (internal PostGIS data)
        if (!zones.dataAvailable()) {
            dataMissing = true;
            findings.add(finding("RESTRICTED_ZONE_DATA_MISSING", FindingSeverity.WARNING,
                    "Hệ thống chưa có dữ liệu vùng hạn chế theo tọa độ GPS để đối chiếu."));
        }
        if (zones.pointInsideRestrictedZone()) {
            findings.add(finding("RESTRICTED_ZONE_INSIDE", FindingSeverity.HIGH,
                    "Điểm giám sát nằm trong khu vực hạn chế " + zoneCodes(zones.affectedZones(), true) + "."));
        } else if (zones.monitoringAreaIntersectsRestrictedZone()) {
            findings.add(finding("RESTRICTED_ZONE_INTERSECTION", FindingSeverity.HIGH,
                    "Vùng giám sát giao với khu vực hạn chế " + zoneCodes(zones.affectedZones(), true) + "."));
        } else if (zones.nearestRestrictedZoneDistanceMeters() != null
                && zones.nearestRestrictedZoneDistanceMeters() <= properties.getRestrictedZoneWarningDistanceM()) {
            findings.add(finding("NEAR_RESTRICTED_ZONE", FindingSeverity.WARNING,
                    "Có khu vực hạn chế cách vùng giám sát " + meters(zones.nearestRestrictedZoneDistanceMeters())
                            + " m (" + zoneCodes(zones.affectedZones(), false) + ")."));
        }

        // 2. Elevation
        if (!elevation.available()) {
            dataMissing = true;
            findings.add(finding("ELEVATION_UNAVAILABLE", FindingSeverity.INFO,
                    "Không thể lấy dữ liệu độ cao địa hình."));
        }

        // 3. OpenStreetMap context
        if (!osm.available()) {
            dataMissing = true;
            findings.add(finding("OSM_UNAVAILABLE", FindingSeverity.INFO,
                    "Không thể lấy dữ liệu khu vực xung quanh từ OpenStreetMap."));
        } else {
            if (osm.queryRadiusMeters() != null && osm.queryRadiusMeters() < radiusMeters) {
                findings.add(finding("OSM_RADIUS_CAPPED", FindingSeverity.INFO,
                        "Dữ liệu OpenStreetMap chỉ được tra cứu trong bán kính " + meters(osm.queryRadiusMeters())
                                + " m quanh điểm giám sát."));
            }
            if (osm.aerodromeNearby()) {
                findings.add(finding("AERODROME_NEARBY", FindingSeverity.WARNING,
                        "Phát hiện sân bay/aerodrome trong khu vực lân cận theo dữ liệu OpenStreetMap."));
            }
            if (osm.helipadNearby()) {
                findings.add(finding("HELIPAD_NEARBY", FindingSeverity.WARNING,
                        "Phát hiện bãi đáp trực thăng trong khu vực lân cận theo dữ liệu OpenStreetMap."));
            }
            NearbyFeature tallest = tallestRelevantStructure(osm, requestedAltitudeAglMeters);
            if (tallest != null) {
                findings.add(finding("STRUCTURE_NEAR_FLIGHT_ALTITUDE", FindingSeverity.WARNING,
                        "Có công trình cao khoảng " + meters(tallest.heightMeters())
                                + " m (theo OpenStreetMap) gần độ cao bay mong muốn "
                                + meters(requestedAltitudeAglMeters) + " m so với mặt đất."));
            }
            int obstacles = osm.towerCount() + osm.mastCount() + osm.powerTowerCount();
            if (obstacles > 0) {
                findings.add(finding("OBSTACLES_NEARBY", FindingSeverity.INFO,
                        "Có " + obstacles + " tháp/cột trong khu vực theo OpenStreetMap; cần xem xét khi lập kế hoạch bay."));
            }
            if (osm.buildingCount() > 0) {
                findings.add(finding("BUILDINGS_PRESENT", FindingSeverity.INFO,
                        "Có " + osm.buildingCount() + " công trình trong khu vực theo OpenStreetMap."));
            }
        }

        FlightAreaRiskLevel level;
        if (findings.stream().anyMatch(item -> item.severity() == FindingSeverity.HIGH)) {
            level = FlightAreaRiskLevel.HIGH_RISK;
        } else if (dataMissing || findings.stream().anyMatch(item -> item.severity() == FindingSeverity.WARNING)) {
            level = FlightAreaRiskLevel.NEEDS_REVIEW;
        } else {
            level = FlightAreaRiskLevel.FAVORABLE;
        }
        return new Assessment(level, List.copyOf(findings), DISCLAIMER);
    }

    /** A structure whose OSM-recorded height reaches the requested altitude (plus a configured margin). */
    private NearbyFeature tallestRelevantStructure(OsmContext osm, Double requestedAltitudeAglMeters) {
        if (requestedAltitudeAglMeters == null) return null;
        double margin = properties.getObstacleClearanceMarginM();
        return osm.importantFeatures().stream()
                .filter(feature -> feature.heightMeters() != null
                        && feature.heightMeters() + margin >= requestedAltitudeAglMeters)
                .max((a, b) -> Double.compare(a.heightMeters(), b.heightMeters()))
                .orElse(null);
    }

    private static String zoneCodes(List<AffectedZone> zones, boolean intersectingOnly) {
        String codes = zones.stream()
                .filter(zone -> !intersectingOnly || zone.intersectsMonitoringArea())
                .map(AffectedZone::code)
                .collect(Collectors.joining(", "));
        return codes.isBlank() ? "đã khai báo" : codes;
    }

    private static String meters(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.0f", value);
    }

    private static AssessmentFinding finding(String code, FindingSeverity severity, String message) {
        return new AssessmentFinding(code, severity, message);
    }
}
