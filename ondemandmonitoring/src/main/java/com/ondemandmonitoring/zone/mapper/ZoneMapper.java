package com.ondemandmonitoring.zone.mapper;

import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.dto.response.ZoneResponse;
import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ZoneMapper {

    WKTWriter WKT_WRITER = new WKTWriter();

    @Mapping(target = "srid", expression = "java(srid(zone.getPolygon()))")
    @Mapping(target = "areaSquareMeters", expression = "java(area(zone.getPolygon()))")
    @Mapping(target = "valid", expression = "java(valid(zone.getPolygon()))")
    @Mapping(target = "polygonWkt", expression = "java(wkt(zone.getPolygon()))")
    @Mapping(target = "coordinates", expression = "java(coordinates(zone.getPolygon()))")
    ZoneResponse toResponse(Zone zone);

    default Integer srid(Polygon polygon) {
        return polygon == null ? null : polygon.getSRID();
    }

    default Double area(Polygon polygon) {
        return polygon == null ? null : polygon.getArea();
    }

    default Boolean valid(Polygon polygon) {
        return polygon == null ? null : polygon.isValid();
    }

    default String wkt(Polygon polygon) {
        return polygon == null ? null : WKT_WRITER.write(polygon);
    }

    default List<List<Double>> coordinates(Polygon polygon) {
        if (polygon == null) {
            return List.of();
        }
        return Arrays.stream(polygon.getExteriorRing().getCoordinates())
                .map(coordinate -> List.of(coordinate.x, coordinate.y))
                .toList();
    }
}
