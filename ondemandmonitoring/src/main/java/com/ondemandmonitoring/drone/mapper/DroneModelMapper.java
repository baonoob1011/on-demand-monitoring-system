package com.ondemandmonitoring.drone.mapper;

import com.ondemandmonitoring.drone.domain.DroneModel;
import com.ondemandmonitoring.drone.dto.request.DroneModelCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneModelUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneModelResponse;
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
