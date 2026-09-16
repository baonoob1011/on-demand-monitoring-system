package com.ondemandmonitoring.drone.mapper;

import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {DroneModelMapper.class, DronePayloadMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DroneMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "droneModel", ignore = true)
    @Mapping(target = "dronePayload", ignore = true)
    Drone toEntity(DroneCreateRequest request);

    DroneResponse toResponse(Drone entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "droneModel", ignore = true)
    @Mapping(target = "dronePayload", ignore = true)
    void updateEntityFromRequest(DroneUpdateRequest request, @MappingTarget Drone entity);
}
