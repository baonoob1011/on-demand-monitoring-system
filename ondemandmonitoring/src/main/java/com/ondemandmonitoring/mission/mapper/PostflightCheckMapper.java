package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.PostflightCheck;
import com.ondemandmonitoring.mission.dto.response.PostflightCheckResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostflightCheckMapper {

    @Mapping(source = "mission.id", target = "missionId")
    @Mapping(source = "drone.droneCode", target = "droneCode")
    PostflightCheckResponse toResponse(PostflightCheck check);
}
