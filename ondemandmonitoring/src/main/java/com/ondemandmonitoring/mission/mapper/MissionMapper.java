package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.dto.response.PlanWaypointResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class MissionMapper {


    @Autowired
    protected MissionPlanRepository missionPlanRepository;


    @Mapping(source = "order.id", target = "orderId")
    public abstract MissionResponse toResponse(Mission mission);






    private boolean isTerminal(Mission mission) {
        return mission.getStatus() == MissionStatus.COMPLETED
                || mission.getStatus() == MissionStatus.FAILED
                || mission.getStatus() == MissionStatus.CANCELLED;
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

    protected Double getRadiusM(Mission mission) {
        if (mission == null || mission.getOrder() == null || mission.getOrder().getDeliverables() == null) {
            return null;
        }

        return mission.getOrder().getDeliverables().stream()
                .filter(deliverable -> deliverable != null && deliverable.getRequirement() != null)
                .map(deliverable -> {
                    Double radius = toDouble(deliverable.getRequirement().get("radiusM"));
                    return radius != null ? radius : toDouble(deliverable.getRequirement().get("radius_m"));
                })
                .filter(radius -> radius != null)
                .findFirst()
                .orElse(null);
    }

    protected Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    protected MissionPlanResponse getPlan(Mission mission) {
        if (mission == null || mission.getId() == null)
            return null;

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
