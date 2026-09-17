package com.ondemandmonitoring.drone.mapper;

import com.ondemandmonitoring.drone.domain.PreflightCheck;
import com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PreflightCheckMapper {

    @Mapping(source = "drone.droneCode", target = "droneCode")
    @Mapping(target = "flightToken", ignore = true)
    PreflightCheckResponse toResponse(PreflightCheck preflightCheck);

    @Mapping(source = "preflightCheck.drone.droneCode", target = "droneCode")
    @Mapping(source = "preflightCheck.id", target = "id")
    @Mapping(source = "preflightCheck.missionId", target = "missionId")
    @Mapping(source = "flightToken", target = "flightToken")
    PreflightCheckResponse toResponse(PreflightCheck preflightCheck, FlightTokenResponse flightToken);
}
