package com.ondemandmonitoring.service.mapper;

import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.dto.request.DeliverableTypeRequest;
import com.ondemandmonitoring.service.dto.response.DeliverableTypeResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface DeliverableTypeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    DeliverableType toEntity(DeliverableTypeRequest request);

    DeliverableTypeResponse toResponse(DeliverableType entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(DeliverableTypeRequest request, @MappingTarget DeliverableType entity);
}
