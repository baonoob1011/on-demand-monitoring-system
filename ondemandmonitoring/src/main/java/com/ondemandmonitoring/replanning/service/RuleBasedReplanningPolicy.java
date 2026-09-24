package com.ondemandmonitoring.replanning.service;

import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.replanning.config.ReplanningProperties;
import com.ondemandmonitoring.replanning.domain.ReplanningReason;
import com.ondemandmonitoring.replanning.dto.ReplanningDecision;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RuleBasedReplanningPolicy implements ReplanningPolicy {

    private final ReplanningProperties properties;

    @Override
    public ReplanningDecision evaluate(DroneTelemetry telemetry, MissionPlan currentPlan) {
        if (!properties.isEnabled()) {
            return ReplanningDecision.none("Dynamic replanning disabled.");
        }
        if (!validPosition(telemetry)) {
            return ReplanningDecision.none("Telemetry has no valid simulation position.");
        }

        ReplanningDecision batteryDecision = evaluateBattery(telemetry, currentPlan);
        if (batteryDecision.required()) {
            return batteryDecision;
        }

        return evaluateRouteDeviation(telemetry, currentPlan);
    }

    private ReplanningDecision evaluateBattery(DroneTelemetry telemetry, MissionPlan currentPlan) {
        if (!validBattery(telemetry.getBatteryPercent())) {
            return ReplanningDecision.none("Telemetry has no valid battery percent.");
        }
        Double estimatedUse = currentPlan.getEstimatedBatteryUsedPercent();
        Double reserve = currentPlan.getSafetyReservePercent();
        if (!finite(estimatedUse)) {
            return ReplanningDecision.none("Current plan has no battery estimate.");
        }
        double reservePercent = finite(reserve) ? reserve : properties.getMinimumBatteryReservePercent();
        double remainingAfterCurrentPlan = telemetry.getBatteryPercent() - estimatedUse;
        if (remainingAfterCurrentPlan + 1.0e-9 < reservePercent) {
            return ReplanningDecision.required(
                    ReplanningReason.REMAINING_ROUTE_INFEASIBLE,
                    String.format(
                            "Battery %.1f%% cannot cover estimated %.1f%% use plus %.1f%% reserve.",
                            telemetry.getBatteryPercent(),
                            estimatedUse,
                            reservePercent));
        }
        return ReplanningDecision.none("Current battery remains above plan reserve.");
    }

    private ReplanningDecision evaluateRouteDeviation(DroneTelemetry telemetry, MissionPlan currentPlan) {
        List<PlanWaypoint> waypoints = currentPlan.getWaypoints().stream()
                .filter(waypoint -> finite(waypoint.getSimX()) && finite(waypoint.getSimY()))
                .sorted(Comparator.comparing(PlanWaypoint::getSequence))
                .toList();
        if (waypoints.size() < 2) {
            return ReplanningDecision.none("Current plan has fewer than two waypoints.");
        }

        double distanceM = distanceToRouteMeters(telemetry.getSimX(), telemetry.getSimY(), waypoints);
        if (distanceM > properties.getRouteDeviationThresholdMeters()) {
            return ReplanningDecision.required(
                    ReplanningReason.ROUTE_DEVIATION,
                    String.format(
                            "Drone is %.1f m away from active route threshold %.1f m.",
                            distanceM,
                            properties.getRouteDeviationThresholdMeters()));
        }
        return ReplanningDecision.none("Drone remains close to active route.");
    }

    private double distanceToRouteMeters(double x, double y, List<PlanWaypoint> waypoints) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            PlanWaypoint a = waypoints.get(i);
            PlanWaypoint b = waypoints.get(i + 1);
            best = Math.min(best, distanceToSegmentMeters(
                    x, y,
                    a.getSimX(), a.getSimY(),
                    b.getSimX(), b.getSimY()));
        }
        return best;
    }

    private double distanceToSegmentMeters(
            double px,
            double py,
            double ax,
            double ay,
            double bx,
            double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 1.0e-12) {
            return Math.hypot(px - ax, py - ay);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double closestX = ax + clamped * dx;
        double closestY = ay + clamped * dy;
        return Math.hypot(px - closestX, py - closestY);
    }

    private boolean validPosition(DroneTelemetry telemetry) {
        return finite(telemetry.getSimX()) && finite(telemetry.getSimY());
    }

    private boolean validBattery(Double value) {
        return finite(value) && value >= 0.0 && value <= 100.0;
    }

    private boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
