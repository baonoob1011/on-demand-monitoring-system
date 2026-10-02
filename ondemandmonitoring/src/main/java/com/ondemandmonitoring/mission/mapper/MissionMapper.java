package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {MissionMapperHelper.class, MissionPlanMapper.class})
public interface MissionMapper {

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "order.orderCode", target = "orderCode")
    @Mapping(source = "order.title", target = "orderTitle")
    @Mapping(source = "order.preferredDateFrom", target = "orderPreferredDateFrom")
    @Mapping(source = "order.preferredDateTo", target = "orderPreferredDateTo")
    @Mapping(source = "order.preferredTime.name", target = "orderPreferredTimeName")
    @Mapping(source = "order.service.name", target = "serviceName")
    @Mapping(source = "order.customer.fullName", target = "customerName")
    @Mapping(source = "order.description", target = "description")
    @Mapping(source = "order.address", target = "address")
    @Mapping(source = "order.rejectReason", target = "rejectionReason")
    @Mapping(source = ".", target = "latitude", qualifiedByName = "missionLatitude")
    @Mapping(source = ".", target = "longitude", qualifiedByName = "missionLongitude")
    @Mapping(source = ".", target = "radiusM", qualifiedByName = "missionRadiusM")
    @Mapping(source = ".", target = "mediaType", qualifiedByName = "missionMediaType")
    @Mapping(source = ".", target = "mediaSummary", qualifiedByName = "missionMediaSummary")
    @Mapping(source = ".", target = "deviceId", qualifiedByName = "missionDeviceId")
    @Mapping(source = ".", target = "deviceCode", qualifiedByName = "missionDeviceCode")
    @Mapping(source = ".", target = "deviceName", qualifiedByName = "missionDeviceName")
    @Mapping(source = ".", target = "staffId", qualifiedByName = "missionStaffId")
    @Mapping(source = ".", target = "operatorId", qualifiedByName = "missionStaffId")
    @Mapping(source = ".", target = "staffAssignments", qualifiedByName = "missionStaffAssignments")
    @Mapping(source = ".", target = "plan", qualifiedByName = "missionPlan")
    MissionResponse toResponse(Mission mission);

    @Mapping(target = "estimatedRemainingBatteryPercent", source = ".", qualifiedByName = "planRemainingBattery")
    @Mapping(target = "waypoints", source = "waypoints", qualifiedByName = "planSortedWaypoints")
    MissionPlanResponse toPlanResponse(MissionPlan plan);
}
