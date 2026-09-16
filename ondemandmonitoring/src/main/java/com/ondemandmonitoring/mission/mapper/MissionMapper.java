package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MissionMapper {
    @Mapping(source = "drone.id", target = "droneId")
    @Mapping(source = "drone.droneCode", target = "droneCode")
    MissionResponse toResponse(Mission mission);
}
