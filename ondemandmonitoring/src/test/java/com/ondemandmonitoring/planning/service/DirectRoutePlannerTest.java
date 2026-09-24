package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.DirectRoutePlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class DirectRoutePlannerTest {

    @Test
    void normalClearRouteIsFeasibleAndReturnsStartAndTarget() {
        FakePlanningEnvironment environment = clearEnvironment(10.0, 15.0);
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        PlannedRoute route = planner.plan(0.0, 0.0, 30.0, 40.0);

        assertThat(route.feasible()).isTrue();
        assertThat(route.distanceM()).isEqualTo(50.0);
        assertThat(route.requiredWorldZM()).isEqualTo(25.0);
        assertThat(route.failureReason()).isNull();
        assertThat(route.points())
                .extracting(PlannedRoute.RoutePoint::simX, PlannedRoute.RoutePoint::simY)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0.0, 0.0),
                        org.assertj.core.groups.Tuple.tuple(30.0, 40.0));
    }

    @Test
    void samplesAlongLineUsingGridResolutionAndIncludesEndpoints() {
        FakePlanningEnvironment environment = clearEnvironment(10.0, 0.0);
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        planner.plan(0.0, 0.0, 25.0, 0.0);

        assertThat(environment.samples)
                .hasSize(4);
        assertThat(environment.samples.get(0).x()).isEqualTo(0.0);
        assertThat(environment.samples.get(1).x()).isCloseTo(25.0 / 3.0, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(environment.samples.get(2).x()).isCloseTo(50.0 / 3.0, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(environment.samples.get(3).x()).isEqualTo(25.0);
    }

    @Test
    void outsideWorldMakesRouteInfeasible() {
        FakePlanningEnvironment environment = new FakePlanningEnvironment(10.0, (x, y) ->
                sample(x, y, x <= 15.0, 0.0, 0.0, false, null, null));
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        PlannedRoute route = planner.plan(0.0, 0.0, 30.0, 0.0);

        assertThat(route.feasible()).isFalse();
        assertThat(route.failureReason()).contains("leaves planning world");
    }

    @Test
    void restrictedZoneMakesRouteInfeasibleAndIdentifiesZone() {
        FakePlanningEnvironment environment = new FakePlanningEnvironment(10.0, (x, y) ->
                sample(x, y, true, 0.0, 0.0, x >= 10.0, "AIRPORT", "Airport"));
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        PlannedRoute route = planner.plan(0.0, 0.0, 20.0, 0.0);

        assertThat(route.feasible()).isFalse();
        assertThat(route.failureReason()).contains("AIRPORT").contains("Airport");
    }

    @Test
    void noDataSurfaceMakesRouteInfeasible() {
        FakePlanningEnvironment environment = new FakePlanningEnvironment(10.0, (x, y) ->
                sample(x, y, true, 0.0, x >= 10.0 ? null : 0.0, false, null, null));
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        PlannedRoute route = planner.plan(0.0, 0.0, 20.0, 0.0);

        assertThat(route.feasible()).isFalse();
        assertThat(route.failureReason()).contains("no planning surface elevation");
    }

    @Test
    void highSurfaceStaysFeasibleAndRaisesRequiredWorldZ() {
        FakePlanningEnvironment environment = new FakePlanningEnvironment(10.0, (x, y) ->
                sample(x, y, true, 0.0, x >= 10.0 ? 42.0 : 5.0, false, null, null));
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        PlannedRoute route = planner.plan(0.0, 0.0, 20.0, 0.0);

        assertThat(route.feasible()).isTrue();
        assertThat(route.requiredWorldZM()).isEqualTo(52.0);
    }

    @Test
    void staticObstacleAloneDoesNotFailRoute() {
        FakePlanningEnvironment environment = new FakePlanningEnvironment(10.0, (x, y) ->
                new EnvironmentSample(x, y, true, 0.0, 90.0, 7.0, false, null, null));
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        PlannedRoute route = planner.plan(0.0, 0.0, 20.0, 0.0);

        assertThat(route.feasible()).isTrue();
        assertThat(route.requiredWorldZM()).isEqualTo(17.0);
    }

    @Test
    void startEqualsTargetIsValidatedWithoutDivisionByZero() {
        FakePlanningEnvironment environment = clearEnvironment(10.0, 4.0);
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        PlannedRoute route = planner.plan(2.0, 3.0, 2.0, 3.0);

        assertThat(route.feasible()).isTrue();
        assertThat(route.distanceM()).isZero();
        assertThat(environment.samples).hasSize(2);
        assertThat(route.points()).hasSize(2);
    }

    @Test
    void veryShortRouteStillSamplesBothEndpoints() {
        FakePlanningEnvironment environment = clearEnvironment(10.0, 4.0);
        DirectRoutePlanner planner = new DirectRoutePlanner(environment);

        PlannedRoute route = planner.plan(0.0, 0.0, 1.0, 0.0);

        assertThat(route.feasible()).isTrue();
        assertThat(environment.samples)
                .extracting(SampledPoint::x)
                .containsExactly(0.0, 1.0);
    }

    private static FakePlanningEnvironment clearEnvironment(double resolutionM, double surfaceElevationM) {
        return new FakePlanningEnvironment(resolutionM, (x, y) ->
                sample(x, y, true, 0.0, surfaceElevationM, false, null, null));
    }

    private static EnvironmentSample sample(
            double x,
            double y,
            boolean insideWorldBounds,
            Double obstacleHeightM,
            Double surfaceElevationM,
            boolean restricted,
            String restrictedZoneCode,
            String restrictedZoneName) {
        return new EnvironmentSample(
                x,
                y,
                insideWorldBounds,
                0.0,
                obstacleHeightM,
                surfaceElevationM,
                restricted,
                restrictedZoneCode,
                restrictedZoneName);
    }

    private static PlanningGrid planningGrid(double resolutionM) {
        return new PlanningGrid(
                "test_world",
                "LOCAL_SIMULATION_METERS_GAZEBO_XY",
                new PlanningGrid.Bounds(-100.0, 100.0, -100.0, 100.0),
                resolutionM,
                1,
                1,
                "nearest-cell",
                List.of(0.0),
                List.of(0.0),
                List.of(0.0));
    }

    private record SampledPoint(double x, double y) {
    }

    private static class FakePlanningEnvironment implements PlanningEnvironment {

        private final PlanningGrid grid;
        private final BiFunction<Double, Double, EnvironmentSample> sampler;
        private final List<SampledPoint> samples = new ArrayList<>();

        private FakePlanningEnvironment(
                double resolutionM,
                BiFunction<Double, Double, EnvironmentSample> sampler) {
            this.grid = planningGrid(resolutionM);
            this.sampler = sampler;
        }

        @Override
        public EnvironmentSample sample(double simX, double simY) {
            samples.add(new SampledPoint(simX, simY));
            return sampler.apply(simX, simY);
        }

        @Override
        public PlanningGrid grid() {
            return grid;
        }
    }
}
