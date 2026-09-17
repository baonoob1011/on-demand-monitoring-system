package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MissionMapper {
    @Mapping(source = "drone.id", target = "droneId")
    @Mapping(source = "drone.droneCode", target = "droneCode")
    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "order.address", target = "address")
    @Mapping(source = "order.mediaType", target = "mediaType")
    @Mapping(target = "latitude", expression = "java(getLatitude(mission))")
    @Mapping(target = "longitude", expression = "java(getLongitude(mission))")
    MissionResponse toResponse(Mission mission);

    default Double getLatitude(Mission mission) {
        if (mission.getOrder() != null && mission.getOrder().getPoint() != null) {
            return mission.getOrder().getPoint().getY();
        }
        return null;
    }

    default Double getLongitude(Mission mission) {
        if (mission.getOrder() != null && mission.getOrder().getPoint() != null) {
            return mission.getOrder().getPoint().getX();
        }
        return null;
    }
}
