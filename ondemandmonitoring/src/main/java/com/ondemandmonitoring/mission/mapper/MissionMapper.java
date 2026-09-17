package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import org.springframework.beans.factory.annotation.Autowired;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;

@Mapper(componentModel = "spring")
public abstract class MissionMapper {

    @Autowired
    protected MissionDroneAssignmentRepository missionDroneAssignmentRepository;

    @Autowired
    protected MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;

    @Mapping(target = "droneId", expression = "java(getDroneId(mission))")
    @Mapping(target = "droneCode", expression = "java(getDroneCode(mission))")
    @Mapping(target = "operatorId", expression = "java(getOperatorId(mission))")
    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "order.address", target = "address")
    @Mapping(source = "order.mediaType", target = "mediaType")
    @Mapping(target = "latitude", expression = "java(getLatitude(mission))")
    @Mapping(target = "longitude", expression = "java(getLongitude(mission))")
    public abstract MissionResponse toResponse(Mission mission);

    protected String getDroneId(Mission mission) {
        if (mission == null || mission.getId() == null) return null;
        return missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())
                .map(mda -> mda.getDrone() != null ? mda.getDrone().getId() : null)
                .orElse(null);
    }

    protected String getDroneCode(Mission mission) {
        if (mission == null || mission.getId() == null) return null;
        return missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())
                .map(mda -> mda.getDrone() != null ? mda.getDrone().getDroneCode() : null)
                .orElse(null);
    }

    protected String getOperatorId(Mission mission) {
        if (mission == null || mission.getId() == null) return null;
        return missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())
                .map(com.ondemandmonitoring.mission.domain.MissionOperatorAssignment::getOperatorId)
                .orElse(null);
    }

    protected Double getLatitude(Mission mission) {
        if (mission.getOrder() != null && mission.getOrder().getPoint() != null) {
            return mission.getOrder().getPoint().getY();
        }
        return null;
    }

    protected Double getLongitude(Mission mission) {
        if (mission.getOrder() != null && mission.getOrder().getPoint() != null) {
            return mission.getOrder().getPoint().getX();
        }
        return null;
    }
}
