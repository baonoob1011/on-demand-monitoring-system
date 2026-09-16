package com.ondemandmonitoring.warehouse.mapper;

import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.dto.request.PreferredTimeCreateRequest;
import com.ondemandmonitoring.warehouse.dto.request.PreferredTimeUpdateRequest;
import com.ondemandmonitoring.warehouse.dto.response.PreferredTimeResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PreferredTimeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    PreferredTime toEntity(PreferredTimeCreateRequest request);

    PreferredTimeResponse toResponse(PreferredTime entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(PreferredTimeUpdateRequest request, @MappingTarget PreferredTime entity);
}
