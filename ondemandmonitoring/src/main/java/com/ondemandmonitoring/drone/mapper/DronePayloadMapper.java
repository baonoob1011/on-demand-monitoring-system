package com.ondemandmonitoring.drone.mapper;

import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.dto.request.DronePayloadCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DronePayloadUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DronePayloadResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DronePayloadMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    DronePayload toEntity(DronePayloadCreateRequest request);

    DronePayloadResponse toResponse(DronePayload entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntityFromRequest(DronePayloadUpdateRequest request, @MappingTarget DronePayload entity);
}
