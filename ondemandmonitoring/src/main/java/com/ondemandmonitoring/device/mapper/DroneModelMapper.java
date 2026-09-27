package com.ondemandmonitoring.device.mapper;

import com.ondemandmonitoring.device.domain.DroneModel;
import com.ondemandmonitoring.device.dto.request.DroneModelCreateRequest;
import com.ondemandmonitoring.device.dto.request.DroneModelUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DroneModelResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DroneModelMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    DroneModel toEntity(DroneModelCreateRequest request);

    DroneModelResponse toResponse(DroneModel entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntityFromRequest(DroneModelUpdateRequest request, @MappingTarget DroneModel entity);
}
