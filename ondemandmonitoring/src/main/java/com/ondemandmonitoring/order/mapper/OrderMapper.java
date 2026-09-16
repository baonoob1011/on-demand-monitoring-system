package com.ondemandmonitoring.order.mapper;

import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.dto.GeoJsonPointDto;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        builder = @Builder(disableBuilder = true),
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderMapper {

    GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "service", ignore = true)
    @Mapping(target = "preferredTime", ignore = true)
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(source = "point", target = "point")
    Order toEntity(OrderCreateRequest request);

    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "customer.fullName", target = "customerName")
    @Mapping(source = "service.id", target = "serviceId")
    @Mapping(source = "service.name", target = "serviceName")
    @Mapping(source = "preferredTime.id", target = "preferredTimeId")
    @Mapping(source = "preferredTime.name", target = "preferredTimeName")
    @Mapping(source = "point", target = "point")
    OrderCreateResponse toResponse(Order order);

    default Point toPoint(GeoJsonPointDto dto) {
        if (dto == null || dto.getCoordinates() == null || dto.getCoordinates().size() < 2) {
            return null;
        }
        Double longitude = dto.getCoordinates().get(0);
        Double latitude = dto.getCoordinates().get(1);
        if (longitude == null || latitude == null) {
            return null;
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    default GeoJsonPointDto toGeoJsonPointDto(Point point) {
        if (point == null) {
            return null;
        }
        return GeoJsonPointDto.of(point.getX(), point.getY());
    }
}
