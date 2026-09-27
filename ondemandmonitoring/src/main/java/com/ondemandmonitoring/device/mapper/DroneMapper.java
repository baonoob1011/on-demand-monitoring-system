package com.ondemandmonitoring.device.mapper;

import com.ondemandmonitoring.device.domain.Drone;
import com.ondemandmonitoring.device.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.device.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DroneResponse;
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
    @Mapping(target = "device", ignore = true)
    @Mapping(target = "droneModel", ignore = true)
    @Mapping(target = "dronePayload", ignore = true)
    Drone toEntity(DroneCreateRequest request);

    @Mapping(source = "device.id", target = "deviceId")
    @Mapping(source = "device.serialNumber", target = "deviceCode")
    @Mapping(source = "device.name", target = "deviceName")
    DroneResponse toResponse(Drone entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "device", ignore = true)
    @Mapping(target = "droneModel", ignore = true)
    @Mapping(target = "dronePayload", ignore = true)
    void updateEntityFromRequest(DroneUpdateRequest request, @MappingTarget Drone entity);
}
