package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FlightTokenMapper {

    FlightTokenResponse toResponse(FlightToken token);
}
