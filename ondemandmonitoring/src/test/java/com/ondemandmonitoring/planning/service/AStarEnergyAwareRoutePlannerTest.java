package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.planning.config.PlanningEnergyProperties;
import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.EnergyEstimateRequest;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.AStarEnergyAwareRoutePlanner;
import com.ondemandmonitoring.planning.service.impl.AStarShortestRoutePlanner;
import com.ondemandmonitoring.planning.service.impl.SimpleMissionEnergyEstimator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AStarEnergyAwareRoutePlannerTest {

    private final PlanningEnergyProperties properties = properties();
    private final SimpleMissionEnergyEstimator estimator = new SimpleMissionEnergyEstimator(properties);

    @Test
    void longerLowerRouteUsesLessEnergyThanShortestHighRoute() {
        GridEnvironment environment = environment(7, 5, 0.0);
        environment.surface(2, 2, 20.0).surface(2, 3, 20.0).surface(2, 4, 20.0);

        PlannedRoute shortest = new AStarShortestRoutePlanner(environment).plan(0.0, 2.0, 6.0, 2.0);
        AStarEnergyAwareRoutePlanner.SearchResult aware = planner(environment)
                .planWithMetrics(0.0, 2.0, 6.0, 2.0);
        double shortestEnergy = estimate(shortest, 0.0).estimatedEnergyMah();

        assertThat(shortest.distanceM()).isEqualTo(6.0);
        assertThat(shortest.requiredWorldZM()).isEqualTo(30.0);
        assertThat(aware.route().distanceM()).isGreaterThan(shortest.distanceM());
        assertThat(aware.route().requiredWorldZM()).isEqualTo(10.0);
        assertThat(aware.objectiveEnergyMah()).isLessThan(shortestEnergy);
        assertThat(aware.objectiveEnergyMah()).isCloseTo(estimate(aware.route(), 0.0).estimatedEnergyMah(), offset());
        System.out.printf("ENERGY_SYNTHETIC|shortestDistance=%.12f|shortestZ=%.12f|shortestEnergy=%.12f|awareDistance=%.12f|awareZ=%.12f|awareEnergy=%.12f%n",
                shortest.distanceM(), shortest.requiredWorldZM(), shortestEnergy,
                aware.route().distanceM(), aware.route().requiredWorldZM(), aware.objectiveEnergyMah());
    }

    @Test
    void smallAltitudeDifferenceDoesNotJustifyDetour() {
        GridEnvironment environment = environment(7, 5, 0.0);
        environment.surface(2, 2, 0.1).surface(2, 3, 0.1).surface(2, 4, 0.1);

        PlannedRoute route = planner(environment).plan(0.0, 2.0, 6.0, 2.0);

        assertThat(route.distanceM()).isEqualTo(6.0);
        assertThat(route.requiredWorldZM()).isEqualTo(10.1);
    }

    @Test
    void equalTerrainMatchesGridShortestDistance() {
        GridEnvironment environment = environment(5, 5, 3.0);
        PlannedRoute shortest = new AStarShortestRoutePlanner(environment).plan(0.0, 0.0, 4.0, 4.0);
        PlannedRoute aware = planner(environment).plan(0.0, 0.0, 4.0, 4.0);

        assertThat(aware.distanceM()).isCloseTo(shortest.distanceM(), offset());
        assertThat(aware.requiredWorldZM()).isEqualTo(shortest.requiredWorldZM());
    }

    @Test
    void climbUsesRequiredWorldZMinusNonZeroHomeWorldZ() {
        GridEnvironment environment = environment(3, 1, 10.0);
        environment.surface(0, 1, 20.0);

        AStarEnergyAwareRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 0.0, 2.0, 0.0);

        double expected = properties.cruiseEnergyMah(2.0) + properties.ascendEnergyMah(20.0);
        assertThat(result.route().requiredWorldZM()).isEqualTo(30.0);
        assertThat(result.objectiveEnergyMah()).isCloseTo(expected, offset());
    }

    @Test
    void progressiveHigherSurfacesChargeOnlyFinalClimbOnce() {
        GridEnvironment environment = environment(6, 1, 10.0);
        environment.surface(0, 2, 20.0).surface(0, 3, 20.0).surface(0, 4, 30.0).surface(0, 5, 30.0);

        AStarEnergyAwareRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 0.0, 5.0, 0.0);

        double expected = properties.cruiseEnergyMah(5.0) + properties.ascendEnergyMah(30.0);
        assertThat(result.route().requiredWorldZM()).isEqualTo(40.0);
        assertThat(result.objectiveEnergyMah()).isCloseTo(expected, offset());
    }

    @Test
    void diagonalUsesActualDistanceAndEnergy() {
        GridEnvironment environment = environment(3, 3, 0.0);
        AStarEnergyAwareRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 0.0, 2.0, 2.0);

        assertThat(result.route().distanceM()).isCloseTo(2.0 * Math.sqrt(2.0), offset());
        assertThat(result.objectiveEnergyMah()).isCloseTo(
                properties.cruiseEnergyMah(2.0 * Math.sqrt(2.0)) + properties.ascendEnergyMah(10.0), offset());
    }

    @Test
    void diagonalCornerCuttingIsBlocked() {
        GridEnvironment environment = environment(2, 2, 0.0).restrict(0, 1).restrict(1, 0);
        assertThat(planner(environment).plan(0.0, 0.0, 1.0, 1.0).feasible()).isFalse();
    }

    @Test
    void restrictedAndNoDataBarriersRemainHardConstraints() {
        GridEnvironment restricted = environment(3, 3, 0.0)
                .restrict(0, 1).restrict(1, 1).restrict(2, 1);
        GridEnvironment noData = environment(3, 3, 0.0)
                .noData(0, 1).noData(1, 1).noData(2, 1);

        assertThat(planner(restricted).plan(0.0, 1.0, 2.0, 1.0).feasible()).isFalse();
        assertThat(planner(noData).plan(0.0, 1.0, 2.0, 1.0).feasible()).isFalse();
    }

    @Test
    void invalidEndpointsAreRejected() {
        GridEnvironment restricted = environment(3, 3, 0.0).restrict(2, 2);
        GridEnvironment noData = environment(3, 3, 0.0).noData(2, 2);

        assertThat(planner(restricted).plan(0.0, 0.0, 2.0, 2.0).failureReason()).contains("restricted");
        assertThat(planner(noData).plan(0.0, 0.0, 2.0, 2.0).failureReason()).contains("no planning surface");
        assertThat(planner(environment(3, 3, 0.0)).plan(-1.0, 0.0, 2.0, 2.0).failureReason()).contains("outside");
    }

    @Test
    void repeatedSearchIsDeterministicAndDistanceFieldIsHorizontalPathDistance() {
        GridEnvironment environment = environment(7, 5, 0.0);
        environment.surface(2, 2, 20.0).surface(2, 3, 20.0).surface(2, 4, 20.0);
        AStarEnergyAwareRoutePlanner planner = planner(environment);

        AStarEnergyAwareRoutePlanner.SearchResult first = planner.planWithMetrics(0.0, 2.0, 6.0, 2.0);
        AStarEnergyAwareRoutePlanner.SearchResult second = planner.planWithMetrics(0.0, 2.0, 6.0, 2.0);

        assertThat(second.route()).isEqualTo(first.route());
        assertThat(second.objectiveEnergyMah()).isEqualTo(first.objectiveEnergyMah());
        assertThat(first.route().distanceM()).isGreaterThan(6.0);
    }

    @Test
    void selectedRouteAltitudeComesOnlyFromItsRawPath() {
        GridEnvironment environment = environment(5, 3, 0.0);
        environment.surface(0, 2, 100.0);

        AStarEnergyAwareRoutePlanner.SearchResult result = planner(environment)
                .planWithMetrics(0.0, 1.0, 4.0, 1.0);

        assertThat(result.route().requiredWorldZM()).isEqualTo(10.0);
        assertThat(result.rawPoints()).allMatch(point -> point.simY() != 0.0 || point.simX() != 2.0);
    }

    private EnergyEstimate estimate(PlannedRoute route, double homeWorldZ) {
        return estimator.estimate(new EnergyEstimateRequest(
                route.distanceM(), homeWorldZ, route.requiredWorldZM(), true, false));
    }

    private AStarEnergyAwareRoutePlanner planner(GridEnvironment environment) {
        return new AStarEnergyAwareRoutePlanner(environment, properties);
    }

    private GridEnvironment environment(int width, int height, double surface) {
        return new GridEnvironment(width, height, surface);
    }

    private PlanningEnergyProperties properties() {
        return new PlanningEnergyProperties(5000.0, 7.5, 5.5, 3.0, 2.0, 1.0, 1.0, 20.0);
    }

    private org.assertj.core.data.Offset<Double> offset() {
        return org.assertj.core.data.Offset.offset(1.0e-9);
    }

    private static final class GridEnvironment implements PlanningEnvironment {
        private final PlanningGrid grid;
        private final boolean[] restricted;
        private final Double[] surfaces;

        private GridEnvironment(int width, int height, double surface) {
            int size = width * height;
            restricted = new boolean[size];
            surfaces = new Double[size];
            List<Double> values = new ArrayList<>(size);
            List<Double> obstacles = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                surfaces[i] = surface;
                values.add(surface);
                obstacles.add(0.0);
            }
            grid = new PlanningGrid("test", "LOCAL_SIMULATION_METERS_GAZEBO_XY",
                    new PlanningGrid.Bounds(0.0, width - 1.0, 0.0, height - 1.0),
                    1.0, width, height, "nearest-cell", values, obstacles, values);
        }

        GridEnvironment surface(int row, int col, double value) { surfaces[index(row, col)] = value; return this; }
        GridEnvironment restrict(int row, int col) { restricted[index(row, col)] = true; return this; }
        GridEnvironment noData(int row, int col) { surfaces[index(row, col)] = null; return this; }

        @Override public EnvironmentSample sample(double x, double y) {
            if (!grid.contains(x, y)) return new EnvironmentSample(x, y, false, null, null, null, false, null, null);
            int i = index(grid.rowForY(y), grid.columnForX(x));
            return new EnvironmentSample(x, y, true, surfaces[i], 0.0, surfaces[i], restricted[i],
                    restricted[i] ? "BLOCKED" : null, restricted[i] ? "Blocked" : null);
        }
        @Override public PlanningGrid grid() { return grid; }
        private int index(int row, int col) { return row * grid.width() + col; }
    }
}
