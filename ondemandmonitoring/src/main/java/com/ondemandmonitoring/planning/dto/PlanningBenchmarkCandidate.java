package com.ondemandmonitoring.planning.dto;

public record PlanningBenchmarkCandidate(
        double targetX,
        double targetY,
        double distanceFromHomeM,
        Double terrainElevationM,
        Double surfaceElevationM,
        int stratumColumn,
        int stratumRow,
        int stratumIndex) {
}
