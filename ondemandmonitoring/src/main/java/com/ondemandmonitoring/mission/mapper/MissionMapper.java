package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.dto.response.PlanWaypointResponse;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;

import org.springframework.beans.factory.annotation.Autowired;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;

@Mapper(componentModel = "spring")
public abstract class MissionMapper {

    @Autowired
    protected MissionDroneAssignmentRepository missionDroneAssignmentRepository;

    @Autowired
    protected MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;

    @Autowired
    protected MissionPlanRepository missionPlanRepository;

    @Mapping(target = "droneId", expression = "java(getDroneId(mission))")
    @Mapping(target = "droneCode", expression = "java(getDroneCode(mission))")
    @Mapping(target = "operatorId", expression = "java(getOperatorId(mission))")
    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "order.title", target = "orderTitle")
    @Mapping(source = "order.customer.fullName", target = "customerName")
    @Mapping(source = "order.address", target = "address")
    @Mapping(target = "mediaType", ignore = true)
    @Mapping(target = "latitude", expression = "java(getLatitude(mission))")
    @Mapping(target = "longitude", expression = "java(getLongitude(mission))")
    @Mapping(target = "plan", expression = "java(getPlan(mission))")
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

    protected MissionPlanResponse getPlan(Mission mission) {
        if (mission == null || mission.getId() == null) return null;

        return missionPlanRepository.findByMissionId(mission.getId())
                .map(this::toPlanResponse)
                .orElse(null);
    }

    public MissionPlanResponse toPlanResponse(MissionPlan plan) {
        return MissionPlanResponse.builder()
                .id(plan.getId())
                .planningAlgorithm(plan.getPlanningAlgorithm())
                .plannedDistanceM(plan.getPlannedDistanceM())
                .plannedDurationSec(plan.getPlannedDurationSec())
                .plannedCruiseSpeedMps(plan.getPlannedCruiseSpeedMps())
                .maxPlannedAltitudeM(plan.getMaxPlannedAltitudeM())
                .estimatedEnergyMah(plan.getEstimatedEnergyMah())
                .estimatedBatteryUsedPercent(plan.getEstimatedBatteryUsedPercent())
                .batteryCapacityMah(plan.getBatteryCapacityMah())
                .availableBatteryPercentAtPlanning(plan.getAvailableBatteryPercentAtPlanning())
                .estimatedRemainingBatteryPercent(estimatedRemainingBatteryPercent(plan))
                .safetyReservePercent(plan.getSafetyReservePercent())
                .requiredBatteryPercent(plan.getRequiredBatteryPercent())
                .feasibilityStatus(plan.getFeasibilityStatus())
                .planningTimeMs(plan.getPlanningTimeMs())
                .planVersion(plan.getPlanVersion())
                .replanningReason(plan.getReplanningReason())
                .replanningStatus(plan.getReplanningStatus())
                .replannedAt(plan.getReplannedAt())
                .waypoints(plan.getWaypoints().stream()
                        .sorted(Comparator.comparing(PlanWaypoint::getSequence))
                        .map(this::toWaypointResponse)
                        .toList())
                .build();
    }

    protected Double estimatedRemainingBatteryPercent(MissionPlan plan) {
        if (plan.getAvailableBatteryPercentAtPlanning() == null
                || plan.getEstimatedBatteryUsedPercent() == null) {
            return null;
        }
        double remaining = plan.getAvailableBatteryPercentAtPlanning() - plan.getEstimatedBatteryUsedPercent();
        return Math.max(0.0, Math.min(100.0, remaining));
    }

    protected PlanWaypointResponse toWaypointResponse(PlanWaypoint waypoint) {
        return PlanWaypointResponse.builder()
                .id(waypoint.getId())
                .sequence(waypoint.getSequence())
                .simX(waypoint.getSimX())
                .simY(waypoint.getSimY())
                .altitudeM(waypoint.getAltitudeM())
                .plannedSpeedMps(waypoint.getPlannedSpeedMps())
                .reason(waypoint.getReason())
                .build();
    }
}
