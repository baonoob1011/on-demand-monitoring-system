package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.AStarShortestRoutePlanner;
import com.ondemandmonitoring.planning.service.impl.DirectRoutePlanner;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("e2e")
class AStarShortestRealMapTest {

    private static final double HOME_X = 0.0;
    private static final double HOME_Y = -280.0;

    @Autowired
    private AStarShortestRoutePlanner aStarPlanner;

    @Autowired
    private DirectRoutePlanner directRoutePlanner;

    @Autowired
    private PlanningEnvironment planningEnvironment;

    @Test
    void runsAgainstCurrentForestPlanningMapAndPostgisZones() {
        long simpleStarted = System.nanoTime();
        AStarShortestRoutePlanner.SearchResult simple =
                aStarPlanner.planWithMetrics(HOME_X, HOME_Y, 200.0, -280.0);
        long simpleTimeMs = elapsedMs(simpleStarted);

        assertThat(simple.route().feasible()).isTrue();
        assertThat(simple.route().distanceM()).isEqualTo(200.0);
        assertThat(simple.route().requiredWorldZM()).isEqualTo(34.0);
        assertThat(simple.rawNodeCount()).isEqualTo(201);
        assertThat(simple.simplifiedPointCount()).isEqualTo(2);
        assertRouteTraversable(simple.route());

        PlannedRoute directAirport = directRoutePlanner.plan(HOME_X, HOME_Y, -200.0, -280.0);
        long airportStarted = System.nanoTime();
        AStarShortestRoutePlanner.SearchResult airport =
                aStarPlanner.planWithMetrics(HOME_X, HOME_Y, -200.0, -280.0);
        long airportTimeMs = elapsedMs(airportStarted);

        assertThat(directAirport.feasible()).isFalse();
        assertThat(directAirport.failureReason()).contains("AIRPORT");
        assertThat(airport.route().feasible()).isFalse();
        assertThat(airport.route().failureReason()).contains("Target").contains("AIRPORT");
        assertThat(airport.expandedNodeCount()).isZero();

        EnvironmentSample detourTarget = planningEnvironment.sample(-400.0, -280.0);
        assertThat(detourTarget.insideWorldBounds()).isTrue();
        assertThat(detourTarget.restricted()).isFalse();
        assertThat(detourTarget.surfaceElevationM()).isNotNull();
        PlannedRoute directDetour = directRoutePlanner.plan(HOME_X, HOME_Y, -400.0, -280.0);
        long detourStarted = System.nanoTime();
        AStarShortestRoutePlanner.SearchResult detour =
                aStarPlanner.planWithMetrics(HOME_X, HOME_Y, -400.0, -280.0);
        long detourTimeMs = elapsedMs(detourStarted);
        assertThat(directDetour.feasible()).isFalse();
        assertThat(directDetour.failureReason()).contains("AIRPORT");
        assertThat(detour.route().feasible()).isTrue();
        assertThat(detour.route().distanceM()).isGreaterThan(400.0);
        assertRouteTraversable(detour.route());

        System.out.printf("ASTAR_REAL|A|feasible=%s|distance=%.12f|raw=%d|simplified=%d|requiredWorldZ=%.12f|expanded=%d|planningMs=%d%n",
                simple.route().feasible(), simple.route().distanceM(), simple.rawNodeCount(),
                simple.simplifiedPointCount(), simple.route().requiredWorldZM(),
                simple.expandedNodeCount(), simpleTimeMs);
        System.out.printf("ASTAR_REAL|B|directFeasible=%s|directReason=%s|astarFeasible=%s|astarReason=%s|raw=%d|simplified=%d|expanded=%d|planningMs=%d%n",
                directAirport.feasible(), directAirport.failureReason().replace('|', '/'),
                airport.route().feasible(), airport.route().failureReason().replace('|', '/'),
                airport.rawNodeCount(), airport.simplifiedPointCount(),
                airport.expandedNodeCount(), airportTimeMs);
        System.out.printf("ASTAR_REAL|DETOUR|target=-400.000,-280.000|directFeasible=%s|directReason=%s|astarFeasible=%s|distance=%.12f|requiredWorldZ=%.12f|raw=%d|simplified=%d|expanded=%d|planningMs=%d%n",
                directDetour.feasible(), directDetour.failureReason().replace('|', '/'),
                detour.route().feasible(), detour.route().distanceM(), detour.route().requiredWorldZM(),
                detour.rawNodeCount(), detour.simplifiedPointCount(),
                detour.expandedNodeCount(), detourTimeMs);
    }

    private void assertRouteTraversable(PlannedRoute route) {
        PlanningEnvironment snapshot = planningEnvironment.snapshot();
        List<PlannedRoute.RoutePoint> points = route.points();
        double step = snapshot.grid().resolutionM();
        for (int i = 1; i < points.size(); i++) {
            PlannedRoute.RoutePoint from = points.get(i - 1);
            PlannedRoute.RoutePoint to = points.get(i);
            double distance = Math.hypot(to.simX() - from.simX(), to.simY() - from.simY());
            int segments = Math.max(1, (int) Math.ceil(distance / step));
            for (int segment = 0; segment <= segments; segment++) {
                double t = (double) segment / segments;
                EnvironmentSample sample = snapshot.sample(
                        from.simX() + (to.simX() - from.simX()) * t,
                        from.simY() + (to.simY() - from.simY()) * t);
                assertThat(sample.insideWorldBounds()).isTrue();
                assertThat(sample.restricted()).isFalse();
                assertThat(sample.surfaceElevationM()).isNotNull();
            }
        }
    }

    private long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
