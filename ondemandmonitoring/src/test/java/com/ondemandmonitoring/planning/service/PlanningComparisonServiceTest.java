package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.planning.dto.AlgorithmPlanningResult;
import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.PlanningComparisonResult;
import com.ondemandmonitoring.planning.dto.PlanningComparisonInput;
import com.ondemandmonitoring.planning.dto.SimulationPoint;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.PlanningComparisonServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mockito;

class PlanningComparisonServiceTest {

    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 0);

    private final MissionRepository missionRepository = Mockito.mock(MissionRepository.class);
    private final MissionDroneAssignmentRepository assignmentRepository = Mockito.mock(MissionDroneAssignmentRepository.class);
    private final DroneTelemetryRepository telemetryRepository = Mockito.mock(DroneTelemetryRepository.class);
    private final RoutePlanner direct = Mockito.mock(RoutePlanner.class);
    private final RoutePlanner shortest = Mockito.mock(RoutePlanner.class);
    private final RoutePlanner aware = Mockito.mock(RoutePlanner.class);
    private final SimulationHomeProvider homeProvider = Mockito.mock(SimulationHomeProvider.class);
    private final PlanningEnvironment environment = Mockito.mock(PlanningEnvironment.class);
    private final MissionEnergyEstimator estimator = Mockito.mock(MissionEnergyEstimator.class);
    private final PlanningComparisonService service = new PlanningComparisonServiceImpl(
            missionRepository, assignmentRepository, telemetryRepository, direct, shortest, aware,
            homeProvider, environment, estimator);

    @Test
    void comparesAllAlgorithmsWithSameInputAndCalculatesTradeoff() {
        arrangeMission(200.0, -280.0);
        when(direct.plan(0.0, -280.0, 200.0, -280.0)).thenReturn(route(200.0, 34.0, 2));
        when(shortest.plan(0.0, -280.0, 200.0, -280.0)).thenReturn(route(200.0, 34.0, 2));
        when(aware.plan(0.0, -280.0, 200.0, -280.0)).thenReturn(route(220.0, 20.0, 4));
        when(estimator.estimate(Mockito.any())).thenAnswer(invocation ->
                invocation.<com.ondemandmonitoring.planning.dto.EnergyEstimateRequest>getArgument(0)
                                .plannedDistanceM() == 220.0
                        ? estimate(120.0, 180.0)
                        : estimate(125.0, 200.0));

        PlanningComparisonResult result = service.compare("mission-1");

        assertThat(result.missionId()).isEqualTo("mission-1");
        assertThat(result.missionCode()).isEqualTo("MISSION-1");
        assertThat(result.homeX()).isZero();
        assertThat(result.homeY()).isEqualTo(-280.0);
        assertThat(result.targetX()).isEqualTo(200.0);
        assertThat(result.homeWorldZ()).isEqualTo(9.4);
        assertThat(result.results()).extracting(AlgorithmPlanningResult::algorithm)
                .containsExactly(PlanningAlgorithm.DIRECT, PlanningAlgorithm.ASTAR_SHORTEST,
                        PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(result.results()).extracting(AlgorithmPlanningResult::waypointCount)
                .containsExactly(2, 2, 4);
        assertThat(result.results()).allMatch(value -> value.availableBatteryPercentAtPlanning() == null);
        assertThat(result.energyAwareVsShortest().energySavingMah()).isEqualTo(20.0);
        assertThat(result.energyAwareVsShortest().energySavingPercent()).isEqualTo(10.0);
        assertThat(result.energyAwareVsShortest().distanceDifferenceM()).isEqualTo(20.0);
        assertThat(result.energyAwareVsShortest().distanceDifferencePercent()).isEqualTo(10.0);
        assertThat(result.energyAwareVsShortest().durationDifferenceSec()).isEqualTo(-5.0);
        assertThat(result.energyAwareVsShortest().durationDifferencePercent()).isEqualTo(-4.0);
        assertThat(result.energyAwareVsShortest().altitudeDifferenceM()).isEqualTo(-14.0);
        verify(homeProvider).home();
        verify(direct).plan(0.0, -280.0, 200.0, -280.0);
        verify(shortest).plan(0.0, -280.0, 200.0, -280.0);
        verify(aware).plan(0.0, -280.0, 200.0, -280.0);
        verify(estimator, Mockito.times(3)).estimate(Mockito.any());
    }

    @Test
    void infeasibleAlgorithmDoesNotStopOthersOrInvokeEstimatorForThatRoute() {
        arrangeMission(-400.0, -280.0);
        when(direct.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(PlannedRoute.failed("Direct route intersects AIRPORT."));
        when(shortest.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(route(500.0, 48.0, 5));
        when(aware.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(route(501.0, 47.0, 6));
        when(estimator.estimate(Mockito.any())).thenReturn(estimate(290.0, 460.0));

        PlanningComparisonResult result = service.compare("mission-1");

        assertThat(result.results().get(0).feasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(result.results().get(0).failureReason()).contains("AIRPORT");
        assertThat(result.results().get(0).waypointCount()).isZero();
        assertThat(result.results().get(0).estimatedEnergyMah()).isNull();
        assertThat(result.results().subList(1, 3))
                .allMatch(value -> value.feasibilityStatus() == FeasibilityStatus.FEASIBLE);
        verify(estimator, Mockito.times(2)).estimate(Mockito.any());
    }

    @Test
    void allInfeasibleReturnsComparisonWithNullTradeoffMetrics() {
        arrangeMission(-200.0, -280.0);
        when(direct.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(PlannedRoute.failed("restricted"));
        when(shortest.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(PlannedRoute.failed("restricted"));
        when(aware.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(PlannedRoute.failed("restricted"));

        PlanningComparisonResult result = service.compare("mission-1");

        assertThat(result.results()).hasSize(3).allMatch(value ->
                value.feasibilityStatus() == FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(result.energyAwareVsShortest().energySavingMah()).isNull();
        assertThat(result.energyAwareVsShortest().distanceDifferencePercent()).isNull();
        verifyNoInteractions(estimator);
    }

    @Test
    void zeroBaselinesProduceNullPercentagesInsteadOfNonFiniteValues() {
        arrangeMission(200.0, -280.0);
        when(direct.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(route(0.0, 9.4, 1));
        when(shortest.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(route(0.0, 9.4, 1));
        when(aware.plan(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(route(0.0, 9.4, 1));
        when(estimator.estimate(Mockito.any())).thenReturn(estimate(0.0, 0.0));

        PlanningComparisonResult result = service.compare("mission-1");

        assertThat(result.energyAwareVsShortest().energySavingPercent()).isNull();
        assertThat(result.energyAwareVsShortest().distanceDifferencePercent()).isNull();
        assertThat(result.energyAwareVsShortest().durationDifferencePercent()).isNull();
    }

    @Test
    void structuralMissionFailureIsNotConvertedToRouteInfeasibility() {
        when(missionRepository.findByIdWithOrder("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare("missing"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission not found: missing");

        verifyNoInteractions(direct, shortest, aware, estimator);
        verify(homeProvider, never()).home();
    }

    @Test
    void explicitInputRunsWithoutMissionAndUsesSuppliedCoordinates() {
        when(environment.sample(10.0, 20.0)).thenReturn(
                new EnvironmentSample(10.0, 20.0, true, 4.0, 0.0, 4.0, false, null, null));
        when(direct.plan(10.0, 20.0, 30.0, 40.0)).thenReturn(route(30.0, 14.0, 2));
        when(shortest.plan(10.0, 20.0, 30.0, 40.0)).thenReturn(route(30.0, 14.0, 2));
        when(aware.plan(10.0, 20.0, 30.0, 40.0)).thenReturn(route(32.0, 12.0, 3));
        when(estimator.estimate(Mockito.any())).thenReturn(estimate(20.0, 25.0));

        PlanningComparisonResult result = service.compare(
                new PlanningComparisonInput("S001", 10.0, 20.0, 30.0, 40.0, null));

        assertThat(result.missionId()).isEqualTo("S001");
        assertThat(result.missionCode()).isNull();
        assertThat(result.homeX()).isEqualTo(10.0);
        assertThat(result.targetY()).isEqualTo(40.0);
        assertThat(result.results()).hasSize(3).allMatch(value ->
                value.availableBatteryPercentAtPlanning() == null);
        verifyNoInteractions(missionRepository, assignmentRepository, telemetryRepository, homeProvider);
        verify(direct).plan(10.0, 20.0, 30.0, 40.0);
        verify(shortest).plan(10.0, 20.0, 30.0, 40.0);
        verify(aware).plan(10.0, 20.0, 30.0, 40.0);
    }

    private void arrangeMission(double targetX, double targetY) {
        Order order = new Order();
        order.setPoint(GEOMETRY.createPoint(new Coordinate(targetX, targetY)));
        Mission mission = new Mission();
        mission.setMissionCode("MISSION-1");
        mission.setOrder(order);
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(homeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(environment.sample(0.0, -280.0)).thenReturn(
                new EnvironmentSample(0.0, -280.0, true, 1.0, 8.4, 9.4, false, null, null));
        when(assignmentRepository.findByMissionIdAndIsCurrentTrue("mission-1")).thenReturn(Optional.empty());
    }

    private PlannedRoute route(double distance, double worldZ, int pointCount) {
        List<PlannedRoute.RoutePoint> points = java.util.stream.IntStream.range(0, pointCount)
                .mapToObj(i -> new PlannedRoute.RoutePoint(i, i, worldZ)).toList();
        return new PlannedRoute(true, distance, worldZ, points, null);
    }

    private EnergyEstimate estimate(double duration, double energyMah) {
        return new EnergyEstimate(duration, duration, 0.0, 0.0, energyMah, energyMah / 50.0,
                2.0, 5000.0, 20.0, 20.0 + energyMah / 50.0);
    }
}
