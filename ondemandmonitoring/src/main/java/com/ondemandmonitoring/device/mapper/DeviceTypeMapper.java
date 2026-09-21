package com.ondemandmonitoring.device.mapper;

import com.ondemandmonitoring.device.domain.DeviceType;
import com.ondemandmonitoring.device.dto.request.DeviceTypeCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceTypeUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceTypeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DeviceTypeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    DeviceType toEntity(DeviceTypeCreateRequest request);

    DeviceTypeResponse toResponse(DeviceType entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntityFromRequest(DeviceTypeUpdateRequest request, @MappingTarget DeviceType entity);
}
