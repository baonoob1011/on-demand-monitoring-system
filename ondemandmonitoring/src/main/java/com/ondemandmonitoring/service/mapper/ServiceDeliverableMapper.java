package com.ondemandmonitoring.service.mapper;

import com.ondemandmonitoring.service.domain.ServiceDeliverable;
import com.ondemandmonitoring.service.dto.response.ServiceDeliverableResponse;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ServiceDeliverableMapper {

    @Mapping(source = "service.id", target = "serviceId")
    @Mapping(source = "service.name", target = "serviceName")
    @Mapping(source = "deliverableType.id", target = "deliverableTypeId")
    @Mapping(source = "deliverableType.name", target = "deliverableTypeName")
    ServiceDeliverableResponse toResponse(ServiceDeliverable entity);
}
