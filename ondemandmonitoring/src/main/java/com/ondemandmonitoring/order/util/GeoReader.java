package com.ondemandmonitoring.order.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

public class GeoReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    public static Point createPoint(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            return null;
        }
        Point p = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        p.setSRID(4326);
        return p;
    }

    public static Double getLongitude(Point point) {
        return point != null ? point.getX() : null;
    }

    public static Double getLatitude(Point point) {
        return point != null ? point.getY() : null;
    }

    @SuppressWarnings("unchecked")
    public static Polygon readPolygonFromCoverageArea(Map<String, Object> coverageArea) {
        if (coverageArea == null || coverageArea.isEmpty()) {
            return null;
        }
        try {
            String type = (String) coverageArea.get("type");
            if (type != null && !"Polygon".equalsIgnoreCase(type)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Coverage area must be a GeoJSON Polygon");
            }
            Object coordinatesObj = coverageArea.get("coordinates");
            if (!(coordinatesObj instanceof List<?> ringsList) || ringsList.isEmpty()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid coordinates in coverageArea GeoJSON");
            }
            List<Coordinate> exteriorCoords = new ArrayList<>();
            List<?> exteriorRing = (List<?>) ringsList.get(0);
            for (Object ptObj : exteriorRing) {
                if (ptObj instanceof List<?> ptList && ptList.size() >= 2) {
                    double lon = ((Number) ptList.get(0)).doubleValue();
                    double lat = ((Number) ptList.get(1)).doubleValue();
                    exteriorCoords.add(new Coordinate(lon, lat));
                }
            }
            if (exteriorCoords.size() < 4) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Polygon ring must have at least 4 coordinates");
            }
            if (!exteriorCoords.get(0).equals2D(exteriorCoords.get(exteriorCoords.size() - 1))) {
                exteriorCoords.add(new Coordinate(exteriorCoords.get(0).x, exteriorCoords.get(0).y));
            }
            LinearRing shell = GEOMETRY_FACTORY.createLinearRing(exteriorCoords.toArray(new Coordinate[0]));

            LinearRing[] holes = null;
            if (ringsList.size() > 1) {
                holes = new LinearRing[ringsList.size() - 1];
                for (int i = 1; i < ringsList.size(); i++) {
                    List<?> holeRing = (List<?>) ringsList.get(i);
                    List<Coordinate> holeCoords = new ArrayList<>();
                    for (Object ptObj : holeRing) {
                        if (ptObj instanceof List<?> ptList && ptList.size() >= 2) {
                            double lon = ((Number) ptList.get(0)).doubleValue();
                            double lat = ((Number) ptList.get(1)).doubleValue();
                            holeCoords.add(new Coordinate(lon, lat));
                        }
                    }
                    if (!holeCoords.isEmpty() && !holeCoords.get(0).equals2D(holeCoords.get(holeCoords.size() - 1))) {
                        holeCoords.add(new Coordinate(holeCoords.get(0).x, holeCoords.get(0).y));
                    }
                    holes[i - 1] = GEOMETRY_FACTORY.createLinearRing(holeCoords.toArray(new Coordinate[0]));
                }
            }
            Polygon polygon = GEOMETRY_FACTORY.createPolygon(shell, holes);
            polygon.setSRID(4326);
            return polygon;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Failed to parse coverageArea GeoJSON: " + e.getMessage());
        }
    }

    public static Map<String, Object> toCoverageArea(Polygon polygon) {
        if (polygon == null) {
            return null;
        }
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("type", "Polygon");
            List<List<List<Double>>> coordinates = new ArrayList<>();

            Coordinate[] shellCoords = polygon.getExteriorRing().getCoordinates();
            List<List<Double>> shellList = new ArrayList<>();
            for (Coordinate c : shellCoords) {
                shellList.add(List.of(c.x, c.y));
            }
            coordinates.add(shellList);

            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                Coordinate[] holeCoords = polygon.getInteriorRingN(i).getCoordinates();
                List<List<Double>> holeList = new ArrayList<>();
                for (Coordinate c : holeCoords) {
                    holeList.add(List.of(c.x, c.y));
                }
                coordinates.add(holeList);
            }
            map.put("coordinates", coordinates);
            return map;
        } catch (Exception e) {
            return null;
        }
    }
}
