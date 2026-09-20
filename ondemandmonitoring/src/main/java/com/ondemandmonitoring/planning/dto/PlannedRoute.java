package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record PlannedRoute(
        boolean feasible,
        double distanceM,
        Double requiredWorldZM,
        List<RoutePoint> points,
        String failureReason) {

    public PlannedRoute {
        points = List.copyOf(points);
    }

    public record RoutePoint(
            double simX,
            double simY,
            double worldZM) {
    }

    public static PlannedRoute failed(String reason) {
        return new PlannedRoute(false, 0.0, null, List.of(), reason);
    }
}
