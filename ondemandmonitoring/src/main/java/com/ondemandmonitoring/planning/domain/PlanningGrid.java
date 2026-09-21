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

        int column = columnForX(simX);
        int row = rowForY(simY);
        int index = row * width + column;

        return new GridSample(
                terrainElevationM.get(index),
                obstacleHeightM.get(index),
                surfaceElevationM.get(index));
    }

    public int columnForX(double simX) {
        return nearestIndex(simX, bounds.minX(), bounds.maxX(), width);
    }

    public int rowForY(double simY) {
        return nearestIndex(simY, bounds.minY(), bounds.maxY(), height);
    }

    public double simXForColumn(int column) {
        return coordinateForIndex(column, width, bounds.minX(), bounds.maxX());
    }

    public double simYForRow(int row) {
        return coordinateForIndex(row, height, bounds.minY(), bounds.maxY());
    }

    private double coordinateForIndex(int index, int size, double min, double max) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Grid index out of bounds: " + index);
        }
        if (index == size - 1) {
            return max;
        }
        return min + index * resolutionM;
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
