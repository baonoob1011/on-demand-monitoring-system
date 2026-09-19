package com.ondemandmonitoring.planning.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanningGrid(
        String world,
        String coordinateSystem,
        Bounds bounds,
        double resolutionM,
        int width,
        int height,
        String lookup,
        List<Double> terrainElevationM,
        List<Double> obstacleHeightM,
        List<Double> surfaceElevationM) {

    public boolean contains(double simX, double simY) {
        return simX >= bounds.minX()
                && simX <= bounds.maxX()
                && simY >= bounds.minY()
                && simY <= bounds.maxY();
    }

    public GridSample sample(double simX, double simY) {
        if (!contains(simX, simY)) {
            return new GridSample(null, null, null);
        }

        int column = nearestIndex(simX, bounds.minX(), bounds.maxX(), width);
        int row = nearestIndex(simY, bounds.minY(), bounds.maxY(), height);
        int index = row * width + column;

        return new GridSample(
                terrainElevationM.get(index),
                obstacleHeightM.get(index),
                surfaceElevationM.get(index));
    }

    private int nearestIndex(double value, double min, double max, int size) {
        if (value <= min) {
            return 0;
        }
        if (value >= max) {
            return size - 1;
        }

        int index = (int) Math.round((value - min) / resolutionM);
        return Math.max(0, Math.min(index, size - 1));
    }

    public record Bounds(
            double minX,
            double maxX,
            double minY,
            double maxY) {
    }

    public record GridSample(
            Double terrainElevationM,
            Double obstacleHeightM,
            Double surfaceElevationM) {
    }
}
