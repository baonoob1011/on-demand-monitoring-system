package com.ondemandmonitoring.zone.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.dto.request.ZoneCreateRequest;
import com.ondemandmonitoring.zone.dto.request.ZonePolygonUpdateRequest;
import com.ondemandmonitoring.zone.dto.response.ZoneResponse;
import com.ondemandmonitoring.zone.mapper.ZoneMapper;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import com.ondemandmonitoring.zone.service.IZoneService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ZoneService implements IZoneService {

    static final String MANUAL_SOURCE_WORLD = "forest_monitoring_compact_manual";
    static final String COORDINATE_SYSTEM = "LOCAL_SIMULATION_METERS_GAZEBO_XY";

    ZoneRepository zoneRepository;
    ZoneMapper zoneMapper;
    GeometryFactory geometryFactory = new GeometryFactory();

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponse> findAll() {
        return zoneRepository.findAll()
                .stream()
                .map(zoneMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ZoneResponse create(ZoneCreateRequest request) {
        Polygon polygon = toPolygon(request.getCoordinates());
        Zone zone = new Zone();
        zone.setName(request.getName().trim());
        zone.setCode(uniqueCode(request.getCode(), request.getName()));
        zone.setZoneType(blankToDefault(request.getZoneType(), "CUSTOM"));
        zone.setPurpose(blankToDefault(request.getPurpose(), "Created from simulation viewer"));
        zone.setSourceWorld(MANUAL_SOURCE_WORLD);
        zone.setCoordinateSystem(COORDINATE_SYSTEM);
        zone.setPolygon(polygon);
        zone.setCenterXM(polygon.getCentroid().getX());
        zone.setCenterYM(polygon.getCentroid().getY());
        zone.setRadiusM(maxDistanceFromCenter(polygon, zone.getCenterXM(), zone.getCenterYM()));
        return zoneMapper.toResponse(zoneRepository.save(zone));
    }

    @Override
    @Transactional
    public ZoneResponse updatePolygon(String id, ZonePolygonUpdateRequest request) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Zone not found: " + id));

        Polygon polygon = toPolygon(request.getCoordinates());
        zone.setPolygon(polygon);
        zone.setCenterXM(polygon.getCentroid().getX());
        zone.setCenterYM(polygon.getCentroid().getY());
        zone.setRadiusM(maxDistanceFromCenter(polygon, zone.getCenterXM(), zone.getCenterYM()));

        return zoneMapper.toResponse(zoneRepository.save(zone));
    }

    @Override
    @Transactional
    public void delete(String id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Zone not found: " + id));
        zoneRepository.delete(zone);
    }

    private Polygon toPolygon(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.size() < 3) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Polygon needs at least 3 points");
        }

        List<List<Double>> normalized = coordinates;
        List<Double> first = normalized.get(0);
        List<Double> last = normalized.get(normalized.size() - 1);
        boolean closed = samePoint(first, last);
        int outputSize = normalized.size() + (closed ? 0 : 1);
        if (outputSize < 4) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Polygon ring must contain at least 4 coordinates including closure");
        }

        Coordinate[] ring = new Coordinate[outputSize];
        for (int i = 0; i < normalized.size(); i++) {
            ring[i] = toCoordinate(normalized.get(i));
        }
        if (!closed) {
            ring[ring.length - 1] = new Coordinate(ring[0]);
        }

        Polygon polygon = geometryFactory.createPolygon(geometryFactory.createLinearRing(ring));
        polygon.setSRID(0);
        if (!polygon.isValid() || polygon.getArea() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Polygon is invalid or has zero area");
        }
        return polygon;
    }

    private Coordinate toCoordinate(List<Double> point) {
        if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Every polygon point must contain x and y");
        }
        return new Coordinate(point.get(0), point.get(1));
    }

    private boolean samePoint(List<Double> a, List<Double> b) {
        return a != null && b != null && a.size() >= 2 && b.size() >= 2
                && Double.compare(a.get(0), b.get(0)) == 0
                && Double.compare(a.get(1), b.get(1)) == 0;
    }

    private String uniqueCode(String requestedCode, String name) {
        String base = slug(blankToDefault(requestedCode, name));
        String candidate = base;
        int suffix = 2;
        while (zoneRepository.existsByCode(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slug(String value) {
        String normalized = value == null ? "CUSTOM_ZONE" : value.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "CUSTOM_ZONE" : normalized;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private double maxDistanceFromCenter(Polygon polygon, double centerX, double centerY) {
        return Arrays.stream(polygon.getExteriorRing().getCoordinates())
                .mapToDouble(point -> Math.hypot(point.x - centerX, point.y - centerY))
                .max()
                .orElse(0);
    }
}
