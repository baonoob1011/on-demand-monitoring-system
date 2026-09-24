package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import com.ondemandmonitoring.planning.service.RoutePlanner;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DirectRoutePlanner implements RoutePlanner {

    private final PlanningEnvironment planningEnvironment;

    public DirectRoutePlanner(PlanningEnvironment planningEnvironment) {
        this.planningEnvironment = planningEnvironment;
    }

    @Override
    public PlannedRoute plan(
            double startX,
            double startY,
            double targetX,
            double targetY) {
        if (!isFinite(startX) || !isFinite(startY) || !isFinite(targetX) || !isFinite(targetY)) {
            return PlannedRoute.failed("Direct route coordinates must be finite numbers.");
        }

        double sampleStepM = planningEnvironment.grid().resolutionM();
        if (!Double.isFinite(sampleStepM) || sampleStepM <= 0.0) {
            return PlannedRoute.failed("Planning grid resolution must be positive.");
        }

        double dx = targetX - startX;
        double dy = targetY - startY;
        double distanceM = Math.hypot(dx, dy);
        int segmentCount = Math.max(1, (int) Math.ceil(distanceM / sampleStepM));

        double maxSurfaceElevationM = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= segmentCount; i++) {
            double t = (double) i / segmentCount;
            double x = startX + dx * t;
            double y = startY + dy * t;

            EnvironmentSample sample = planningEnvironment.sample(x, y);
            PlannedRoute invalidRoute = validateSample(sample);
            if (invalidRoute != null) {
                return invalidRoute;
            }

            maxSurfaceElevationM = Math.max(maxSurfaceElevationM, sample.surfaceElevationM());
        }

        // This is a planning-map Gazebo world-Z clearance value, not PX4 relative altitude/NED.
        double requiredWorldZM = maxSurfaceElevationM + RoutePlanningPolicy.SAFETY_CLEARANCE_M;
        return new PlannedRoute(
                true,
                distanceM,
                requiredWorldZM,
                List.of(
                        new PlannedRoute.RoutePoint(startX, startY, requiredWorldZM),
                        new PlannedRoute.RoutePoint(targetX, targetY, requiredWorldZM)),
                null);
    }

    private PlannedRoute validateSample(EnvironmentSample sample) {
        if (!sample.insideWorldBounds()) {
            return PlannedRoute.failed(String.format(
                    "Direct route leaves planning world near x=%.2f, y=%.2f.",
                    sample.simX(),
                    sample.simY()));
        }

        if (sample.restricted()) {
            String zoneLabel = sample.restrictedZoneCode() != null
                    ? sample.restrictedZoneCode()
                    : "restricted zone";
            if (sample.restrictedZoneName() != null && !sample.restrictedZoneName().isBlank()) {
                zoneLabel += " (" + sample.restrictedZoneName() + ")";
            }
            return PlannedRoute.failed(String.format(
                    "Direct route intersects restricted zone %s near x=%.2f, y=%.2f.",
                    zoneLabel,
                    sample.simX(),
                    sample.simY()));
        }

        if (sample.surfaceElevationM() == null) {
            return PlannedRoute.failed(String.format(
                    "Direct route has no planning surface elevation near x=%.2f, y=%.2f.",
                    sample.simX(),
                    sample.simY()));
        }

        return null;
    }

    private boolean isFinite(double value) {
        return Double.isFinite(value);
    }
}
