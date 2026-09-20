package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.config.PlanningEnergyProperties;
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

@Service("aStarEnergyAwareRoutePlanner")
public class AStarEnergyAwareRoutePlanner implements RoutePlanner {

    private static final byte UNKNOWN = 0;
    private static final byte TRAVERSABLE = 1;
    private static final byte BLOCKED = 2;
    private static final double EPSILON = 1.0e-12;
    private static final double SQRT_TWO = Math.sqrt(2.0);
    private static final int[] ROW_OFFSETS = {-1, 0, 1, 0, -1, -1, 1, 1};
    private static final int[] COLUMN_OFFSETS = {0, 1, 0, -1, 1, -1, 1, -1};

    private final PlanningEnvironment planningEnvironment;
    private final PlanningEnergyProperties energy;

    public AStarEnergyAwareRoutePlanner(
            PlanningEnvironment planningEnvironment,
            PlanningEnergyProperties energy) {
        this.planningEnvironment = planningEnvironment;
        this.energy = energy;
    }

    @Override
    public PlannedRoute plan(double startX, double startY, double targetX, double targetY) {
        return planWithMetrics(startX, startY, targetX, targetY).route();
    }

    public SearchResult planWithMetrics(double startX, double startY, double targetX, double targetY) {
        if (!finite(startX) || !finite(startY) || !finite(targetX) || !finite(targetY)) {
            return failed("Energy-aware route coordinates must be finite numbers.");
        }
        PlanningEnvironment environment = planningEnvironment.snapshot();
        PlanningGrid grid = environment.grid();
        if (!finite(grid.resolutionM()) || grid.resolutionM() <= 0.0 || grid.width() <= 0 || grid.height() <= 0) {
            return failed("Planning grid resolution and dimensions must be positive.");
        }
        EnvironmentSample startSample = environment.sample(startX, startY);
        EnvironmentSample targetSample = environment.sample(targetX, targetY);
        String failure = endpointFailure("Start", startSample);
        if (failure == null) failure = endpointFailure("Target", targetSample);
        if (failure != null) return failed(failure);

        int width = grid.width();
        int cellCount = Math.multiplyExact(width, grid.height());
        int startIndex = index(grid.rowForY(startY), grid.columnForX(startX), width);
        int targetIndex = index(grid.rowForY(targetY), grid.columnForX(targetX), width);
        byte[] cellStates = new byte[cellCount];
        double[] surfaces = new double[cellCount];
        Arrays.fill(surfaces, Double.NaN);
        if (!traversable(startIndex, environment, grid, cellStates, surfaces)
                || !traversable(targetIndex, environment, grid, cellStates, surfaces)) {
            return failed("Start or target maps to a non-traversable planning cell.");
        }

        double homeWorldZ = startSample.surfaceElevationM();
        @SuppressWarnings("unchecked")
        List<Label>[] labelsByCell = (List<Label>[]) new List<?>[cellCount];
        PriorityQueue<Label> open = new PriorityQueue<>(Comparator
                .comparingDouble(Label::fEnergyMah)
                .thenComparingDouble(Label::gEnergyMah)
                .thenComparingDouble(Label::maxSurfaceM)
                .thenComparingInt(Label::cellIndex)
                .thenComparingLong(Label::serial));
        long serial = 0;
        Label start = label(startIndex, 0.0, surfaces[startIndex], homeWorldZ, null, serial++, targetIndex, grid);
        addLabel(labelsByCell, start);
        open.add(start);
        int expanded = 0;
        int generated = 1;
        int peakOpen = 1;

        while (!open.isEmpty()) {
            Label current = open.poll();
            if (!current.active()) continue;
            expanded++;
            if (current.cellIndex() == targetIndex) {
                return success(current, startX, startY, targetX, targetY, homeWorldZ,
                        expanded, generated, peakOpen, grid);
            }
            int row = current.cellIndex() / width;
            int column = current.cellIndex() % width;
            for (int direction = 0; direction < ROW_OFFSETS.length; direction++) {
                int dr = ROW_OFFSETS[direction];
                int dc = COLUMN_OFFSETS[direction];
                int nr = row + dr;
                int nc = column + dc;
                if (nr < 0 || nr >= grid.height() || nc < 0 || nc >= width) continue;
                int nextIndex = index(nr, nc, width);
                if (!traversable(nextIndex, environment, grid, cellStates, surfaces)) continue;
                if (dr != 0 && dc != 0
                        && (!traversable(index(row, nc, width), environment, grid, cellStates, surfaces)
                        || !traversable(index(nr, column, width), environment, grid, cellStates, surfaces))) {
                    continue;
                }
                double step = grid.resolutionM() * (dr != 0 && dc != 0 ? SQRT_TWO : 1.0);
                double distance = current.distanceM() + step;
                double maxSurface = Math.max(current.maxSurfaceM(), surfaces[nextIndex]);
                Label candidate = label(nextIndex, distance, maxSurface, homeWorldZ,
                        current, serial++, targetIndex, grid);
                if (dominated(labelsByCell[nextIndex], candidate)) continue;
                removeDominated(labelsByCell[nextIndex], candidate);
                addLabel(labelsByCell, candidate);
                open.add(candidate);
                generated++;
            }
            peakOpen = Math.max(peakOpen, open.size());
        }
        return new SearchResult(PlannedRoute.failed("No traversable energy-aware route exists between start and target."),
                0.0, List.of(), 0, 0, expanded, generated, peakOpen);
    }

    private Label label(int cellIndex, double distance, double maxSurface, double homeWorldZ,
            Label previous, long serial, int targetIndex, PlanningGrid grid) {
        double requiredZ = maxSurface + RoutePlanningPolicy.SAFETY_CLEARANCE_M;
        double climb = Math.max(0.0, requiredZ - homeWorldZ);
        double g = energy.cruiseEnergyMah(distance) + energy.ascendEnergyMah(climb);
        int row = cellIndex / grid.width();
        int col = cellIndex % grid.width();
        int targetRow = targetIndex / grid.width();
        int targetCol = targetIndex % grid.width();
        double remaining = Math.hypot(targetRow - row, targetCol - col) * grid.resolutionM();
        return new Label(cellIndex, distance, maxSurface, g, g + energy.cruiseEnergyMah(remaining),
                previous, serial);
    }

    private boolean dominated(List<Label> labels, Label candidate) {
        if (labels == null) return false;
        return labels.stream().anyMatch(existing -> existing.active()
                && existing.distanceM() <= candidate.distanceM() + EPSILON
                && existing.maxSurfaceM() <= candidate.maxSurfaceM() + EPSILON);
    }

    private void removeDominated(List<Label> labels, Label candidate) {
        if (labels == null) return;
        labels.stream().filter(existing -> existing.active()
                && candidate.distanceM() <= existing.distanceM() + EPSILON
                && candidate.maxSurfaceM() <= existing.maxSurfaceM() + EPSILON)
                .forEach(Label::deactivate);
    }

    private void addLabel(List<Label>[] labelsByCell, Label label) {
        if (labelsByCell[label.cellIndex()] == null) labelsByCell[label.cellIndex()] = new ArrayList<>();
        labelsByCell[label.cellIndex()].add(label);
    }

    private SearchResult success(Label goal, double startX, double startY, double targetX, double targetY,
            double homeWorldZ, int expanded, int generated, int peakOpen, PlanningGrid grid) {
        List<Label> chain = new ArrayList<>();
        for (Label label = goal; label != null; label = label.previous()) chain.add(label);
        Collections.reverse(chain);
        List<Label> simplified = simplify(chain, grid.width());
        double requiredZ = goal.maxSurfaceM() + RoutePlanningPolicy.SAFETY_CLEARANCE_M;
        List<PlannedRoute.RoutePoint> raw = points(chain, requiredZ, grid);
        List<PlannedRoute.RoutePoint> points = new ArrayList<>(points(simplified, requiredZ, grid));
        points.set(0, new PlannedRoute.RoutePoint(startX, startY, requiredZ));
        points.set(points.size() - 1, new PlannedRoute.RoutePoint(targetX, targetY, requiredZ));
        double objective = energy.cruiseEnergyMah(goal.distanceM())
                + energy.ascendEnergyMah(Math.max(0.0, requiredZ - homeWorldZ));
        return new SearchResult(new PlannedRoute(true, goal.distanceM(), requiredZ, points, null),
                objective, raw, chain.size(), points.size(), expanded, generated, peakOpen);
    }

    private List<PlannedRoute.RoutePoint> points(List<Label> labels, double z, PlanningGrid grid) {
        return labels.stream().map(label -> new PlannedRoute.RoutePoint(
                grid.simXForColumn(label.cellIndex() % grid.width()),
                grid.simYForRow(label.cellIndex() / grid.width()), z)).toList();
    }

    private List<Label> simplify(List<Label> raw, int width) {
        if (raw.size() <= 2) return List.copyOf(raw);
        List<Label> result = new ArrayList<>();
        result.add(raw.getFirst());
        int oldDr = direction(raw.get(1).cellIndex() / width - raw.get(0).cellIndex() / width);
        int oldDc = direction(raw.get(1).cellIndex() % width - raw.get(0).cellIndex() % width);
        for (int i = 1; i < raw.size() - 1; i++) {
            int dr = direction(raw.get(i + 1).cellIndex() / width - raw.get(i).cellIndex() / width);
            int dc = direction(raw.get(i + 1).cellIndex() % width - raw.get(i).cellIndex() % width);
            if (dr != oldDr || dc != oldDc) {
                result.add(raw.get(i));
                oldDr = dr;
                oldDc = dc;
            }
        }
        result.add(raw.getLast());
        return List.copyOf(result);
    }

    private boolean traversable(int cell, PlanningEnvironment environment, PlanningGrid grid,
            byte[] states, double[] surfaces) {
        if (states[cell] != UNKNOWN) return states[cell] == TRAVERSABLE;
        EnvironmentSample sample = environment.sample(
                grid.simXForColumn(cell % grid.width()), grid.simYForRow(cell / grid.width()));
        boolean valid = sample.insideWorldBounds() && !sample.restricted() && sample.surfaceElevationM() != null;
        states[cell] = valid ? TRAVERSABLE : BLOCKED;
        if (valid) surfaces[cell] = sample.surfaceElevationM();
        return valid;
    }

    private String endpointFailure(String name, EnvironmentSample sample) {
        if (!sample.insideWorldBounds()) return name + " is outside the planning world.";
        if (sample.restricted()) return name + " is inside restricted zone "
                + (sample.restrictedZoneCode() == null ? "restricted zone" : sample.restrictedZoneCode()) + ".";
        if (sample.surfaceElevationM() == null) return name + " has no planning surface elevation.";
        return null;
    }

    private SearchResult failed(String reason) {
        return new SearchResult(PlannedRoute.failed(reason), 0.0, List.of(), 0, 0, 0, 0, 0);
    }

    private int index(int row, int column, int width) { return row * width + column; }
    private int direction(int value) { return Integer.compare(value, 0); }
    private boolean finite(double value) { return Double.isFinite(value); }

    private static final class Label {
        private final int cellIndex;
        private final double distanceM;
        private final double maxSurfaceM;
        private final double gEnergyMah;
        private final double fEnergyMah;
        private final Label previous;
        private final long serial;
        private boolean active = true;

        private Label(int cellIndex, double distanceM, double maxSurfaceM, double gEnergyMah,
                double fEnergyMah, Label previous, long serial) {
            this.cellIndex = cellIndex; this.distanceM = distanceM; this.maxSurfaceM = maxSurfaceM;
            this.gEnergyMah = gEnergyMah; this.fEnergyMah = fEnergyMah; this.previous = previous; this.serial = serial;
        }
        int cellIndex() { return cellIndex; } double distanceM() { return distanceM; }
        double maxSurfaceM() { return maxSurfaceM; } double gEnergyMah() { return gEnergyMah; }
        double fEnergyMah() { return fEnergyMah; } Label previous() { return previous; }
        long serial() { return serial; } boolean active() { return active; } void deactivate() { active = false; }
    }

    public record SearchResult(PlannedRoute route, double objectiveEnergyMah,
            List<PlannedRoute.RoutePoint> rawPoints, int rawNodeCount, int simplifiedPointCount,
            int expandedStateCount, int generatedStateCount, int peakOpenSize) {
        public SearchResult { rawPoints = List.copyOf(rawPoints); }
    }
}
