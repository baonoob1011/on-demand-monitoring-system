package com.ondemandmonitoring.order.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categoryService", ignore = true)
    @Mapping(target = "orderStatus", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Order toEntity(OrderCreateRequest request);

    @Mapping(source = "categoryService.id", target = "categoryServiceId")
    @Mapping(target = "updatedAt", ignore = true)
    OrderCreateResponse toResponse(Order order);
}
