package com.ondemandmonitoring.order.mapper;

import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderDeliverable;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.dto.response.OrderDeliverableResponse;
import com.ondemandmonitoring.order.util.GeoReader;
import java.util.Map;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, builder = @Builder(disableBuilder = true), nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "service", ignore = true)
    @Mapping(target = "preferredTime", ignore = true)
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "rejectReason", ignore = true)
    @Mapping(target = "reviewBy", ignore = true)
    @Mapping(target = "reviewAt", ignore = true)
    @Mapping(target = "deliverables", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "point", expression = "java(toPoint(request.getLongitude(), request.getLatitude()))")
    @Mapping(target = "targetArea", expression = "java(toPolygon(request.getCoverageArea()))")
    Order toEntity(OrderCreateRequest request);

    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "customer.fullName", target = "customerName")
    @Mapping(source = "service.id", target = "serviceId")
    @Mapping(source = "service.name", target = "serviceName")
    @Mapping(source = "preferredTime.id", target = "preferredTimeId")
    @Mapping(source = "preferredTime.name", target = "preferredTimeName")
    @Mapping(source = "reviewBy.id", target = "reviewById")
    @Mapping(source = "reviewBy.fullName", target = "reviewByName")
    @Mapping(target = "longitude", expression = "java(toLongitude(order.getPoint()))")
    @Mapping(target = "latitude", expression = "java(toLatitude(order.getPoint()))")
    @Mapping(target = "radiusM", expression = "java(toRadiusM(order))")
    @Mapping(target = "coverageArea", expression = "java(toCoverageArea(order.getTargetArea()))")
    @Mapping(source = "deliverables", target = "deliverables")
    OrderCreateResponse toResponse(Order order);

    @Mapping(source = "deliverableType.id", target = "deliverableTypeId")
    @Mapping(source = "deliverableType.name", target = "deliverableTypeName")
    @Mapping(source = "deliverableType.defaultFormat", target = "defaultFormat")
    OrderDeliverableResponse toDeliverableResponse(OrderDeliverable entity);

    default Point toPoint(Double longitude, Double latitude) {
        return GeoReader.createPoint(longitude, latitude);
    }

    default Polygon toPolygon(Map<String, Object> coverageArea) {
        return GeoReader.readPolygonFromCoverageArea(coverageArea);
    }

    default Double toLongitude(Point point) {
        return GeoReader.getLongitude(point);
    }

    default Double toLatitude(Point point) {
        return GeoReader.getLatitude(point);
    }

    default Map<String, Object> toCoverageArea(Polygon polygon) {
        return GeoReader.toCoverageArea(polygon);
    }

    default Double toRadiusM(Order order) {
        if (order == null || order.getDeliverables() == null) {
            return null;
        }
        for (OrderDeliverable deliverable : order.getDeliverables()) {
            if (deliverable == null || deliverable.getRequirement() == null) {
                continue;
            }
            Double radius = toDouble(deliverable.getRequirement().get("radiusM"));
            if (radius == null) {
                radius = toDouble(deliverable.getRequirement().get("radius_m"));
            }
            if (radius != null) {
                return radius;
            }
        }
        return null;
    }

    default Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
