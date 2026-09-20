package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import com.ondemandmonitoring.planning.service.RoutePlanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import org.springframework.stereotype.Service;

@Service("aStarShortestRoutePlanner")
public class AStarShortestRoutePlanner implements RoutePlanner {

    private static final byte UNKNOWN = 0;
    private static final byte TRAVERSABLE = 1;
    private static final byte BLOCKED = 2;
    private static final double SQRT_TWO = Math.sqrt(2.0);
    private static final int[] ROW_OFFSETS = {-1, 0, 1, 0, -1, -1, 1, 1};
    private static final int[] COLUMN_OFFSETS = {0, 1, 0, -1, 1, -1, 1, -1};

    private final PlanningEnvironment planningEnvironment;

    public AStarShortestRoutePlanner(PlanningEnvironment planningEnvironment) {
        this.planningEnvironment = planningEnvironment;
    }

    @Override
    public PlannedRoute plan(double startX, double startY, double targetX, double targetY) {
        return planWithMetrics(startX, startY, targetX, targetY).route();
    }

    public SearchResult planWithMetrics(double startX, double startY, double targetX, double targetY) {
        if (!isFinite(startX) || !isFinite(startY) || !isFinite(targetX) || !isFinite(targetY)) {
            return failed("A* route coordinates must be finite numbers.", 0);
        }

        PlanningEnvironment environment = planningEnvironment.snapshot();
        PlanningGrid grid = environment.grid();
        if (!Double.isFinite(grid.resolutionM()) || grid.resolutionM() <= 0.0) {
            return failed("Planning grid resolution must be positive.", 0);
        }
        if (grid.width() <= 0 || grid.height() <= 0) {
            return failed("Planning grid dimensions must be positive.", 0);
        }

        EnvironmentSample startSample = environment.sample(startX, startY);
        String startFailure = endpointFailure("Start", startSample);
        if (startFailure != null) {
            return failed(startFailure, 0);
        }
        EnvironmentSample targetSample = environment.sample(targetX, targetY);
        String targetFailure = endpointFailure("Target", targetSample);
        if (targetFailure != null) {
            return failed(targetFailure, 0);
        }

        int width = grid.width();
        int height = grid.height();
        int cellCount = Math.multiplyExact(width, height);
        int startRow = grid.rowForY(startY);
        int startColumn = grid.columnForX(startX);
        int targetRow = grid.rowForY(targetY);
        int targetColumn = grid.columnForX(targetX);
        int startIndex = index(startRow, startColumn, width);
        int targetIndex = index(targetRow, targetColumn, width);

        byte[] cellStates = new byte[cellCount];
        double[] surfaceElevations = new double[cellCount];
        Arrays.fill(surfaceElevations, Double.NaN);
        if (!isTraversable(startIndex, environment, grid, cellStates, surfaceElevations)
                || !isTraversable(targetIndex, environment, grid, cellStates, surfaceElevations)) {
            return failed("Start or target maps to a non-traversable planning cell.", 0);
        }

        if (startIndex == targetIndex) {
            double requiredWorldZ = Math.max(
                    Math.max(startSample.surfaceElevationM(), targetSample.surfaceElevationM()),
                    surfaceElevations[startIndex]) + RoutePlanningPolicy.SAFETY_CLEARANCE_M;
            List<PlannedRoute.RoutePoint> points = List.of(
                    new PlannedRoute.RoutePoint(startX, startY, requiredWorldZ),
                    new PlannedRoute.RoutePoint(targetX, targetY, requiredWorldZ));
            return new SearchResult(
                    new PlannedRoute(true, Math.hypot(targetX - startX, targetY - startY), requiredWorldZ, points, null),
                    1,
                    points.size(),
                    1);
        }

        double[] gScore = new double[cellCount];
        Arrays.fill(gScore, Double.POSITIVE_INFINITY);
        int[] cameFrom = new int[cellCount];
        Arrays.fill(cameFrom, -1);
        boolean[] closed = new boolean[cellCount];
        PriorityQueue<QueueEntry> openSet = new PriorityQueue<>(Comparator
                .comparingDouble(QueueEntry::fScore)
                .thenComparingDouble(QueueEntry::heuristic)
                .thenComparingInt(QueueEntry::index));

        gScore[startIndex] = 0.0;
        double startHeuristic = heuristic(startRow, startColumn, targetRow, targetColumn, grid.resolutionM());
        openSet.add(new QueueEntry(startIndex, 0.0, startHeuristic, startHeuristic));
        int expandedNodes = 0;

        while (!openSet.isEmpty()) {
            QueueEntry current = openSet.poll();
            if (current.gScore() > gScore[current.index()]) {
                continue;
            }
            if (closed[current.index()]) {
                continue;
            }
            closed[current.index()] = true;
            expandedNodes++;

            if (current.index() == targetIndex) {
                return buildSuccessfulResult(
                        startX, startY, targetX, targetY,
                        startSample, targetSample,
                        startIndex, targetIndex,
                        cameFrom, gScore[targetIndex], expandedNodes,
                        environment, grid, cellStates, surfaceElevations);
            }

            int currentRow = current.index() / width;
            int currentColumn = current.index() % width;
            for (int direction = 0; direction < ROW_OFFSETS.length; direction++) {
                int rowOffset = ROW_OFFSETS[direction];
                int columnOffset = COLUMN_OFFSETS[direction];
                int neighborRow = currentRow + rowOffset;
                int neighborColumn = currentColumn + columnOffset;
                if (neighborRow < 0 || neighborRow >= height || neighborColumn < 0 || neighborColumn >= width) {
                    continue;
                }

                int neighborIndex = index(neighborRow, neighborColumn, width);
                if (closed[neighborIndex]
                        || !isTraversable(neighborIndex, environment, grid, cellStates, surfaceElevations)) {
                    continue;
                }
                if (rowOffset != 0 && columnOffset != 0
                        && !diagonalAllowed(
                                currentRow, currentColumn, rowOffset, columnOffset,
                                environment, grid, cellStates, surfaceElevations)) {
                    continue;
                }

                double stepCost = grid.resolutionM()
                        * (rowOffset != 0 && columnOffset != 0 ? SQRT_TWO : 1.0);
                double tentativeG = gScore[current.index()] + stepCost;
                if (tentativeG >= gScore[neighborIndex]) {
                    continue;
                }

                cameFrom[neighborIndex] = current.index();
                gScore[neighborIndex] = tentativeG;
                double h = heuristic(neighborRow, neighborColumn, targetRow, targetColumn, grid.resolutionM());
                openSet.add(new QueueEntry(neighborIndex, tentativeG, tentativeG + h, h));
            }
        }

        return failed("No traversable A* route exists between start and target.", expandedNodes);
    }

    private SearchResult buildSuccessfulResult(
            double startX,
            double startY,
            double targetX,
            double targetY,
            EnvironmentSample startSample,
            EnvironmentSample targetSample,
            int startIndex,
            int targetIndex,
            int[] cameFrom,
            double gridDistance,
            int expandedNodes,
            PlanningEnvironment environment,
            PlanningGrid grid,
            byte[] cellStates,
            double[] surfaceElevations) {
        List<Integer> rawPath = reconstructPath(startIndex, targetIndex, cameFrom);
        List<Integer> simplifiedPath = simplify(rawPath, grid.width());

        double maxSurface = Math.max(startSample.surfaceElevationM(), targetSample.surfaceElevationM());
        for (int cellIndex : rawPath) {
            isTraversable(cellIndex, environment, grid, cellStates, surfaceElevations);
            maxSurface = Math.max(maxSurface, surfaceElevations[cellIndex]);
        }
        double requiredWorldZ = maxSurface + RoutePlanningPolicy.SAFETY_CLEARANCE_M;

        List<PlannedRoute.RoutePoint> points = new ArrayList<>(simplifiedPath.size());
        for (int cellIndex : simplifiedPath) {
            int row = cellIndex / grid.width();
            int column = cellIndex % grid.width();
            points.add(new PlannedRoute.RoutePoint(
                    grid.simXForColumn(column),
                    grid.simYForRow(row),
                    requiredWorldZ));
        }
        points.set(0, new PlannedRoute.RoutePoint(startX, startY, requiredWorldZ));
        points.set(points.size() - 1, new PlannedRoute.RoutePoint(targetX, targetY, requiredWorldZ));

        double startConnector = Math.hypot(
                startX - grid.simXForColumn(startIndex % grid.width()),
                startY - grid.simYForRow(startIndex / grid.width()));
        double targetConnector = Math.hypot(
                targetX - grid.simXForColumn(targetIndex % grid.width()),
                targetY - grid.simYForRow(targetIndex / grid.width()));
        double distance = gridDistance + startConnector + targetConnector;
        PlannedRoute route = new PlannedRoute(true, distance, requiredWorldZ, points, null);
        return new SearchResult(route, rawPath.size(), points.size(), expandedNodes);
    }

    private boolean diagonalAllowed(
            int currentRow,
            int currentColumn,
            int rowOffset,
            int columnOffset,
            PlanningEnvironment environment,
            PlanningGrid grid,
            byte[] cellStates,
            double[] surfaceElevations) {
        int width = grid.width();
        int horizontal = index(currentRow, currentColumn + columnOffset, width);
        int vertical = index(currentRow + rowOffset, currentColumn, width);
        return isTraversable(horizontal, environment, grid, cellStates, surfaceElevations)
                && isTraversable(vertical, environment, grid, cellStates, surfaceElevations);
    }

    private boolean isTraversable(
            int cellIndex,
            PlanningEnvironment environment,
            PlanningGrid grid,
            byte[] cellStates,
            double[] surfaceElevations) {
        if (cellStates[cellIndex] != UNKNOWN) {
            return cellStates[cellIndex] == TRAVERSABLE;
        }
        int row = cellIndex / grid.width();
        int column = cellIndex % grid.width();
        double x = grid.simXForColumn(column);
        double y = grid.simYForRow(row);
        EnvironmentSample sample = environment.sample(x, y);
        boolean traversable = sample.insideWorldBounds()
                && !sample.restricted()
                && sample.surfaceElevationM() != null;
        cellStates[cellIndex] = traversable ? TRAVERSABLE : BLOCKED;
        if (traversable) {
            surfaceElevations[cellIndex] = sample.surfaceElevationM();
        }
        return traversable;
    }

    private List<Integer> reconstructPath(int startIndex, int targetIndex, int[] cameFrom) {
        List<Integer> path = new ArrayList<>();
        int current = targetIndex;
        while (current != -1) {
            path.add(current);
            if (current == startIndex) {
                break;
            }
            current = cameFrom[current];
        }
        Collections.reverse(path);
        return path;
    }

    private List<Integer> simplify(List<Integer> rawPath, int width) {
        if (rawPath.size() <= 2) {
            return List.copyOf(rawPath);
        }
        List<Integer> simplified = new ArrayList<>();
        simplified.add(rawPath.get(0));
        int previousRowDirection = direction(rawPath.get(1) / width - rawPath.get(0) / width);
        int previousColumnDirection = direction(rawPath.get(1) % width - rawPath.get(0) % width);
        for (int i = 1; i < rawPath.size() - 1; i++) {
            int current = rawPath.get(i);
            int next = rawPath.get(i + 1);
            int rowDirection = direction(next / width - current / width);
            int columnDirection = direction(next % width - current % width);
            if (rowDirection != previousRowDirection || columnDirection != previousColumnDirection) {
                simplified.add(current);
                previousRowDirection = rowDirection;
                previousColumnDirection = columnDirection;
            }
        }
        simplified.add(rawPath.get(rawPath.size() - 1));
        return List.copyOf(simplified);
    }

    private int direction(int value) {
        return Integer.compare(value, 0);
    }

    private String endpointFailure(String label, EnvironmentSample sample) {
        if (!sample.insideWorldBounds()) {
            return label + " is outside the planning world.";
        }
        if (sample.restricted()) {
            String zone = sample.restrictedZoneCode() == null ? "restricted zone" : sample.restrictedZoneCode();
            return label + " is inside restricted zone " + zone + ".";
        }
        if (sample.surfaceElevationM() == null) {
            return label + " has no planning surface elevation.";
        }
        return null;
    }

    private double heuristic(int row, int column, int targetRow, int targetColumn, double resolution) {
        return Math.hypot(targetRow - row, targetColumn - column) * resolution;
    }

    private int index(int row, int column, int width) {
        return row * width + column;
    }

    private boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    private SearchResult failed(String reason, int expandedNodes) {
        return new SearchResult(PlannedRoute.failed(reason), 0, 0, expandedNodes);
    }

    private record QueueEntry(int index, double gScore, double fScore, double heuristic) {
    }

    public record SearchResult(
            PlannedRoute route,
            int rawNodeCount,
            int simplifiedPointCount,
            int expandedNodeCount) {
    }
}
