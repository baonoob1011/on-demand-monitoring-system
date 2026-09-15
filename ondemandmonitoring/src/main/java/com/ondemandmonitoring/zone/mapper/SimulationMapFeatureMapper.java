package com.ondemandmonitoring.zone.mapper;

import com.ondemandmonitoring.zone.domain.SimulationMapFeature;
import com.ondemandmonitoring.zone.dto.response.SimulationMapFeatureResponse;
import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SimulationMapFeatureMapper {

    WKTWriter WKT_WRITER = new WKTWriter();

    @Mapping(target = "geometryType", expression = "java(geometryType(feature.getGeometry()))")
    @Mapping(target = "srid", expression = "java(srid(feature.getGeometry()))")
    @Mapping(target = "areaSquareMeters", expression = "java(area(feature.getGeometry()))")
    @Mapping(target = "lengthMeters", expression = "java(length(feature.getGeometry()))")
    @Mapping(target = "valid", expression = "java(valid(feature.getGeometry()))")
    @Mapping(target = "geometryWkt", expression = "java(wkt(feature.getGeometry()))")
    @Mapping(target = "coordinates", expression = "java(coordinates(feature.getGeometry()))")
    SimulationMapFeatureResponse toResponse(SimulationMapFeature feature);

    default String geometryType(Geometry geometry) {
        return geometry == null ? null : geometry.getGeometryType();
    }

    default Integer srid(Geometry geometry) {
        return geometry == null ? null : geometry.getSRID();
    }

    default Double area(Geometry geometry) {
        return geometry == null ? null : geometry.getArea();
    }

    default Double length(Geometry geometry) {
        return geometry == null ? null : geometry.getLength();
    }

    default Boolean valid(Geometry geometry) {
        return geometry == null ? null : geometry.isValid();
    }

    default String wkt(Geometry geometry) {
        return geometry == null ? null : WKT_WRITER.write(geometry);
    }

    default List<List<Double>> coordinates(Geometry geometry) {
        if (geometry == null) {
            return List.of();
        }
        return Arrays.stream(geometry.getCoordinates())
                .map(coordinate -> List.of(coordinate.x, coordinate.y))
                .toList();
    }
}
