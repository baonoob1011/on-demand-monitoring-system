package com.ondemandmonitoring.planning.dto.response;

public record PlanningGridMetadataResponse(
        String world,
        String coordinateSystem,
        double minX,
        double maxX,
        double minY,
        double maxY,
        double resolutionM,
        int width,
        int height,
        String lookup) {
}
