package com.ondemandmonitoring.device.mapper;

import com.ondemandmonitoring.device.domain.PreflightCheck;
import com.ondemandmonitoring.device.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PreflightCheckMapper {

    @Mapping(source = "device.deviceCode", target = "deviceCode")
    @Mapping(target = "flightToken", ignore = true)
    PreflightCheckResponse toResponse(PreflightCheck preflightCheck);

    @Mapping(source = "preflightCheck.device.deviceCode", target = "deviceCode")
    @Mapping(source = "preflightCheck.id", target = "id")
    @Mapping(source = "preflightCheck.missionId", target = "missionId")
    @Mapping(source = "flightToken", target = "flightToken")
    PreflightCheckResponse toResponse(PreflightCheck preflightCheck, FlightTokenResponse flightToken);
}
