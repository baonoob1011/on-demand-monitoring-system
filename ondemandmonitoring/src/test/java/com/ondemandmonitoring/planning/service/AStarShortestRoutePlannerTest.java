package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.AStarShortestRoutePlanner;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AStarShortestRoutePlannerTest {

    @Test
    void straightUnobstructedRouteIsShortestAndSimplified() {
        GridEnvironment environment = environment(5, 3);

        AStarShortestRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 1.0, 4.0, 1.0);

        assertThat(result.route().feasible()).isTrue();
        assertThat(result.route().distanceM()).isEqualTo(4.0);
        assertThat(result.rawNodeCount()).isEqualTo(5);
        assertThat(result.simplifiedPointCount()).isEqualTo(2);
        assertThat(result.route().points())
                .extracting(PlannedRoute.RoutePoint::simX, PlannedRoute.RoutePoint::simY)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0.0, 1.0),
                        org.assertj.core.groups.Tuple.tuple(4.0, 1.0));
    }

    @Test
    void restrictedBarrierWithOpeningProducesDetour() {
        GridEnvironment environment = environment(5, 3);
        environment.restrict(1, 2).restrict(0, 2);

        AStarShortestRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 1.0, 4.0, 1.0);

        assertThat(result.route().feasible()).isTrue();
        assertThat(result.route().distanceM()).isGreaterThan(4.0);
        assertThat(result.simplifiedPointCount()).isGreaterThan(2);
        assertThat(result.route().points()).noneMatch(point -> environment.cellRestricted(point.simX(), point.simY()));
    }

    @Test
    void completelyBlockingRestrictedBarrierHasNoRoute() {
        GridEnvironment environment = environment(5, 3);
        environment.restrict(0, 2).restrict(1, 2).restrict(2, 2);

        PlannedRoute route = planner(environment).plan(0.0, 1.0, 4.0, 1.0);

        assertThat(route.feasible()).isFalse();
        assertThat(route.failureReason()).contains("No traversable A* route");
    }

    @Test
    void noDataBarrierWithOpeningProducesDetour() {
        GridEnvironment environment = environment(5, 3);
        environment.noData(0, 2).noData(1, 2);

        PlannedRoute route = planner(environment).plan(0.0, 1.0, 4.0, 1.0);

        assertThat(route.feasible()).isTrue();
        assertThat(route.distanceM()).isGreaterThan(4.0);
    }

    @Test
    void restrictedTargetIsRejectedBeforeSearch() {
        GridEnvironment environment = environment(3, 3).restrict(2, 2);

        PlannedRoute route = planner(environment).plan(0.0, 0.0, 2.0, 2.0);

        assertThat(route.feasible()).isFalse();
        assertThat(route.failureReason()).contains("Target").contains("restricted");
    }

    @Test
    void restrictedStartIsRejectedBeforeSearch() {
        GridEnvironment environment = environment(3, 3).restrict(0, 0);

        PlannedRoute route = planner(environment).plan(0.0, 0.0, 2.0, 2.0);

        assertThat(route.feasible()).isFalse();
        assertThat(route.failureReason()).contains("Start").contains("restricted");
    }

    @Test
    void noDataTargetIsRejectedBeforeSearch() {
        GridEnvironment environment = environment(3, 3).noData(2, 2);

        PlannedRoute route = planner(environment).plan(0.0, 0.0, 2.0, 2.0);

        assertThat(route.feasible()).isFalse();
        assertThat(route.failureReason()).contains("Target").contains("no planning surface");
    }

    @Test
    void outsideWorldIsRejectedBeforeSearch() {
        PlannedRoute route = planner(environment(3, 3)).plan(-1.0, 0.0, 2.0, 2.0);

        assertThat(route.feasible()).isFalse();
        assertThat(route.failureReason()).contains("Start").contains("outside");
    }

    @Test
    void diagonalMovementUsesResolutionScaledSquareRootTwoCost() {
        GridEnvironment environment = environment(3, 3, 2.0);

        AStarShortestRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 0.0, 4.0, 4.0);

        assertThat(result.route().distanceM()).isCloseTo(4.0 * Math.sqrt(2.0), offset());
        assertThat(result.rawNodeCount()).isEqualTo(3);
        assertThat(result.simplifiedPointCount()).isEqualTo(2);
    }

    @Test
    void diagonalCannotCutBetweenTwoBlockedOrthogonalCells() {
        GridEnvironment environment = environment(2, 2)
                .restrict(0, 1)
                .restrict(1, 0);

        PlannedRoute route = planner(environment).plan(0.0, 0.0, 1.0, 1.0);

        assertThat(route.feasible()).isFalse();
    }

    @Test
    void detourDistanceUsesPathCostInsteadOfDirectDistance() {
        GridEnvironment environment = environment(7, 5);
        for (int row = 0; row < 4; row++) {
            environment.restrict(row, 3);
        }

        PlannedRoute route = planner(environment).plan(0.0, 2.0, 6.0, 2.0);

        assertThat(route.feasible()).isTrue();
        assertThat(route.distanceM()).isGreaterThan(Math.hypot(6.0, 0.0));
    }

    @Test
    void highestSurfaceDeterminesGazeboWorldZClearance() {
        GridEnvironment environment = environment(5, 1);
        environment.surface(0, 2, 42.0);

        PlannedRoute route = planner(environment).plan(0.0, 0.0, 4.0, 0.0);

        assertThat(route.feasible()).isTrue();
        assertThat(route.requiredWorldZM()).isEqualTo(52.0);
        assertThat(route.points()).allMatch(point -> point.worldZM() == 52.0);
    }

    @Test
    void directionChangesRemainWhileCollinearNodesAreRemoved() {
        GridEnvironment environment = environment(4, 3);
        environment.restrict(0, 3).restrict(1, 3);

        AStarShortestRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 0.0, 3.0, 2.0);

        assertThat(result.route().feasible()).isTrue();
        assertThat(result.rawNodeCount()).isGreaterThan(result.simplifiedPointCount());
        assertThat(result.route().points().getFirst().simX()).isEqualTo(0.0);
        assertThat(result.route().points().getLast().simX()).isEqualTo(3.0);
        assertThat(result.route().points().getLast().simY()).isEqualTo(2.0);
    }

    @Test
    void stairStepRouteIsCompressedWhenStraightSegmentIsSafe() {
        GridEnvironment environment = environment(8, 4);

        AStarShortestRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 0.0, 7.0, 3.0);

        assertThat(result.route().feasible()).isTrue();
        assertThat(result.rawNodeCount()).isGreaterThan(2);
        assertThat(result.simplifiedPointCount()).isEqualTo(2);
        assertThat(result.route().points())
                .extracting(PlannedRoute.RoutePoint::simX, PlannedRoute.RoutePoint::simY)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0.0, 0.0),
                        org.assertj.core.groups.Tuple.tuple(7.0, 3.0));
    }

    @Test
    void repeatedSearchProducesIdenticalRoute() {
        GridEnvironment environment = environment(6, 5);
        environment.restrict(1, 2).restrict(2, 2).restrict(3, 2);
        AStarShortestRoutePlanner planner = planner(environment);

        PlannedRoute first = planner.plan(0.0, 2.0, 5.0, 2.0);
        PlannedRoute second = planner.plan(0.0, 2.0, 5.0, 2.0);

        assertThat(second).isEqualTo(first);
    }

    private AStarShortestRoutePlanner planner(GridEnvironment environment) {
        return new AStarShortestRoutePlanner(environment);
    }

    private GridEnvironment environment(int width, int height) {
        return environment(width, height, 1.0);
    }

    private GridEnvironment environment(int width, int height, double resolution) {
        return new GridEnvironment(width, height, resolution);
    }

    private org.assertj.core.data.Offset<Double> offset() {
        return org.assertj.core.data.Offset.offset(1.0e-9);
    }

    private static final class GridEnvironment implements PlanningEnvironment {

        private final PlanningGrid grid;
        private final boolean[] restricted;
        private final Double[] surfaces;

        private GridEnvironment(int width, int height, double resolution) {
            int size = width * height;
            restricted = new boolean[size];
            surfaces = new Double[size];
            ArraysSupport.fill(surfaces, 1.0);
            List<Double> elevations = new ArrayList<>(size);
            List<Double> obstacles = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                elevations.add(1.0);
                obstacles.add(0.0);
            }
            grid = new PlanningGrid(
                    "test_world",
                    "LOCAL_SIMULATION_METERS_GAZEBO_XY",
                    new PlanningGrid.Bounds(0.0, (width - 1) * resolution, 0.0, (height - 1) * resolution),
                    resolution,
                    width,
                    height,
                    "nearest-cell",
                    elevations,
                    obstacles,
                    elevations);
        }

        private GridEnvironment restrict(int row, int column) {
            restricted[index(row, column)] = true;
            return this;
        }

        private GridEnvironment noData(int row, int column) {
            surfaces[index(row, column)] = null;
            return this;
        }

        private GridEnvironment surface(int row, int column, double value) {
            surfaces[index(row, column)] = value;
            return this;
        }

        private boolean cellRestricted(double x, double y) {
            return restricted[index(grid.rowForY(y), grid.columnForX(x))];
        }

        @Override
        public EnvironmentSample sample(double simX, double simY) {
            boolean inside = grid.contains(simX, simY);
            if (!inside) {
                return new EnvironmentSample(simX, simY, false, null, null, null, false, null, null);
            }
            int index = index(grid.rowForY(simY), grid.columnForX(simX));
            return new EnvironmentSample(
                    simX,
                    simY,
                    true,
                    surfaces[index],
                    0.0,
                    surfaces[index],
                    restricted[index],
                    restricted[index] ? "BLOCKED" : null,
                    restricted[index] ? "Blocked test cell" : null);
        }

        @Override
        public PlanningGrid grid() {
            return grid;
        }

        private int index(int row, int column) {
            return row * grid.width() + column;
        }
    }

    private static final class ArraysSupport {
        private ArraysSupport() {
        }

        private static void fill(Double[] values, double value) {
            for (int i = 0; i < values.length; i++) {
                values[i] = value;
            }
        }
    }
}
