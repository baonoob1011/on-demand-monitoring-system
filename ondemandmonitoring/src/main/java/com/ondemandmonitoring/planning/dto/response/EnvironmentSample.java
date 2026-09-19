package com.ondemandmonitoring.planning.dto.response;

public record EnvironmentSample(
        double simX,
        double simY,
        boolean insideWorldBounds,
        Double terrainElevationM,
        Double obstacleHeightM,
        Double surfaceElevationM,
        boolean restricted,
        String restrictedZoneCode,
        String restrictedZoneName) {
}
