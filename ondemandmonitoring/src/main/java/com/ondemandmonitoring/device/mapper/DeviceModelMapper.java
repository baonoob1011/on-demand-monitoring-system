package com.ondemandmonitoring.device.mapper;

import com.ondemandmonitoring.device.domain.DeviceModel;
import com.ondemandmonitoring.device.dto.request.DeviceModelCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceModelUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceModelResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DeviceTypeMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DeviceModelMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deviceTypes", ignore = true)
    DeviceModel toEntity(DeviceModelCreateRequest request);

    DeviceModelResponse toResponse(DeviceModel entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deviceTypes", ignore = true)
    void updateEntityFromRequest(DeviceModelUpdateRequest request, @MappingTarget DeviceModel entity);
}
