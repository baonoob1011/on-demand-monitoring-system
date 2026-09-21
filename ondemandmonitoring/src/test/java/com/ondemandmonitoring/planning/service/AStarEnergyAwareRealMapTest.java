package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.EnergyEstimateRequest;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.AStarEnergyAwareRoutePlanner;
import com.ondemandmonitoring.planning.service.impl.AStarShortestRoutePlanner;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("e2e")
class AStarEnergyAwareRealMapTest {

    @Autowired private AStarShortestRoutePlanner shortestPlanner;
    @Autowired private AStarEnergyAwareRoutePlanner energyAwarePlanner;
    @Autowired private MissionEnergyEstimator energyEstimator;
    @Autowired private PlanningEnvironment planningEnvironment;

    @Test
    void comparesRequiredRealMapScenarios() {
        compare("SIMPLE", 200.0, -280.0);
        compare("AIRPORT_DETOUR", -400.0, -280.0);
    }

    private void compare(String label, double targetX, double targetY) {
        long shortestStarted = System.nanoTime();
        AStarShortestRoutePlanner.SearchResult shortest =
                shortestPlanner.planWithMetrics(0.0, -280.0, targetX, targetY);
        long shortestMs = elapsed(shortestStarted);
        long awareStarted = System.nanoTime();
        AStarEnergyAwareRoutePlanner.SearchResult aware =
                energyAwarePlanner.planWithMetrics(0.0, -280.0, targetX, targetY);
        long awareMs = elapsed(awareStarted);

        assertThat(shortest.route().feasible()).isTrue();
        assertThat(aware.route().feasible()).isTrue();
        double homeWorldZ = planningEnvironment.sample(0.0, -280.0).surfaceElevationM();
        EnergyEstimate shortestEnergy = estimate(shortest.route(), homeWorldZ);
        EnergyEstimate awareEnergy = estimate(aware.route(), homeWorldZ);
        assertThat(aware.objectiveEnergyMah()).isCloseTo(
                awareEnergy.estimatedEnergyMah(), org.assertj.core.data.Offset.offset(1.0e-9));
        assertRawRouteSafe(aware.rawPoints());

        System.out.printf("ENERGY_REAL|%s|target=%.3f,%.3f|shortestDistance=%.12f|shortestZ=%.12f|shortestEnergy=%.12f|shortestRaw=%d|shortestSimplified=%d|shortestExpanded=%d|shortestMs=%d|awareDistance=%.12f|awareZ=%.12f|awareEnergy=%.12f|awareRaw=%d|awareSimplified=%d|awareExpanded=%d|awareGenerated=%d|awarePeakOpen=%d|awareMs=%d%n",
                label, targetX, targetY, shortest.route().distanceM(), shortest.route().requiredWorldZM(),
                shortestEnergy.estimatedEnergyMah(), shortest.rawNodeCount(), shortest.simplifiedPointCount(),
                shortest.expandedNodeCount(), shortestMs, aware.route().distanceM(), aware.route().requiredWorldZM(),
                awareEnergy.estimatedEnergyMah(), aware.rawNodeCount(), aware.simplifiedPointCount(),
                aware.expandedStateCount(), aware.generatedStateCount(), aware.peakOpenSize(), awareMs);
    }

    private EnergyEstimate estimate(PlannedRoute route, double homeWorldZ) {
        return energyEstimator.estimate(new EnergyEstimateRequest(
                route.distanceM(), homeWorldZ, route.requiredWorldZM(), true, false));
    }

    private void assertRawRouteSafe(List<PlannedRoute.RoutePoint> points) {
        PlanningEnvironment snapshot = planningEnvironment.snapshot();
        for (PlannedRoute.RoutePoint point : points) {
            EnvironmentSample sample = snapshot.sample(point.simX(), point.simY());
            assertThat(sample.insideWorldBounds()).isTrue();
            assertThat(sample.restricted()).isFalse();
            assertThat(sample.surfaceElevationM()).isNotNull();
        }
    }

    private long elapsed(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }
}
