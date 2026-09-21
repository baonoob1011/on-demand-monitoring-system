package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.enums.WaypointReason;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.EnergyEstimateRequest;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.SimulationPoint;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.MissionPlanningServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class MissionPlanningServiceTest {

    private static final GeometryFactory LOCAL_GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 0);
    private static final GeometryFactory WGS84_GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final MissionRepository missionRepository = org.mockito.Mockito.mock(MissionRepository.class);
    private final MissionPlanRepository missionPlanRepository = org.mockito.Mockito.mock(MissionPlanRepository.class);
    private final MissionDroneAssignmentRepository missionDroneAssignmentRepository = org.mockito.Mockito.mock(MissionDroneAssignmentRepository.class);
    private final DroneTelemetryRepository droneTelemetryRepository = org.mockito.Mockito.mock(DroneTelemetryRepository.class);
    private final RoutePlanner directRoutePlanner = org.mockito.Mockito.mock(RoutePlanner.class);
    private final RoutePlanner aStarShortestRoutePlanner = org.mockito.Mockito.mock(RoutePlanner.class);
    private final RoutePlanner aStarEnergyAwareRoutePlanner = org.mockito.Mockito.mock(RoutePlanner.class);
    private final SimulationHomeProvider simulationHomeProvider = org.mockito.Mockito.mock(SimulationHomeProvider.class);
    private final PlanningEnvironment planningEnvironment = org.mockito.Mockito.mock(PlanningEnvironment.class);
    private final MissionEnergyEstimator missionEnergyEstimator = org.mockito.Mockito.mock(MissionEnergyEstimator.class);
    private final MissionPlanningService service = new MissionPlanningServiceImpl(
            missionRepository,
            missionPlanRepository,
            missionDroneAssignmentRepository,
            droneTelemetryRepository,
            directRoutePlanner,
            aStarShortestRoutePlanner,
            aStarEnergyAwareRoutePlanner,
            simulationHomeProvider,
            planningEnvironment,
            missionEnergyEstimator);

    @Test
    void existingMissionWithOrderTargetPlansFromConfiguredHomeToOrderPoint() {
        PlannedRoute expectedRoute = feasibleRoute();
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(missionWithOrder(point(260.0, 230.0))));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(directRoutePlanner.plan(0.0, -280.0, 260.0, 230.0)).thenReturn(expectedRoute);

        PlannedRoute route = service.validateDirectRoute("mission-1");

        assertThat(route).isSameAs(expectedRoute);
        verify(directRoutePlanner).plan(0.0, -280.0, 260.0, 230.0);
    }

    @Test
    void missingMissionFailsClearly() {
        when(missionRepository.findByIdWithOrder("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateDirectRoute("missing"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission not found: missing")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSION_NOT_FOUND);

        verifyNoInteractions(directRoutePlanner);
    }

    @Test
    void missionWithoutOrderFailsClearly() {
        Mission mission = new Mission();
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> service.validateDirectRoute("mission-1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission mission-1 has no order.");

        verifyNoInteractions(directRoutePlanner);
    }

    @Test
    void orderWithoutPointFailsClearly() {
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(missionWithOrder(null)));

        assertThatThrownBy(() -> service.validateDirectRoute("mission-1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission mission-1 order has no target point.");

        verifyNoInteractions(directRoutePlanner);
    }

    @Test
    void epsg4326LongitudeLatitudeIsNotSentToGazeboPlanner() {
        Point longitudeLatitude = WGS84_GEOMETRY_FACTORY.createPoint(new Coordinate(-122.378, 37.7983));
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(missionWithOrder(longitudeLatitude)));

        assertThatThrownBy(() -> service.validateDirectRoute("mission-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("EPSG:4326 longitude/latitude");

        verifyNoInteractions(directRoutePlanner);
    }

    @Test
    void infeasibleDirectRouteIsReturnedUnchanged() {
        PlannedRoute expectedRoute = PlannedRoute.failed("Direct route intersects restricted zone.");
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(missionWithOrder(point(260.0, 230.0))));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(directRoutePlanner.plan(0.0, -280.0, 260.0, 230.0)).thenReturn(expectedRoute);

        PlannedRoute route = service.validateDirectRoute("mission-1");

        assertThat(route).isSameAs(expectedRoute);
        assertThat(route.feasible()).isFalse();
    }

    @Test
    void homeCoordinatesComeFromProvider() {
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(missionWithOrder(point(260.0, 230.0))));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(12.0, -345.0));
        when(directRoutePlanner.plan(12.0, -345.0, 260.0, 230.0)).thenReturn(feasibleRoute());

        service.validateDirectRoute("mission-1");

        ArgumentCaptor<Double> startX = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> startY = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> targetX = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> targetY = ArgumentCaptor.forClass(Double.class);
        verify(directRoutePlanner).plan(startX.capture(), startY.capture(), targetX.capture(), targetY.capture());
        assertThat(startX.getValue()).isEqualTo(12.0);
        assertThat(startY.getValue()).isEqualTo(-345.0);
        assertThat(targetX.getValue()).isEqualTo(260.0);
        assertThat(targetY.getValue()).isEqualTo(230.0);
    }

    @Test
    void feasibleDirectRoutePersistsMissionPlanAndStartTargetWaypoints() {
        Mission mission = missionWithOrder(point(260.0, 230.0));
        PlannedRoute route = new PlannedRoute(
                true,
                576.98,
                55.0,
                List.of(
                        new PlannedRoute.RoutePoint(0.0, -280.0, 55.0),
                        new PlannedRoute.RoutePoint(260.0, 230.0, 55.0)),
                null);
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.empty());
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(planningEnvironment.sample(0.0, -280.0)).thenReturn(homeSample(12.0));
        when(directRoutePlanner.plan(0.0, -280.0, 260.0, 230.0)).thenReturn(route);
        when(missionEnergyEstimator.estimate(Mockito.argThat(request ->
                request.plannedDistanceM() == 576.98
                        && request.homeWorldZ() == 12.0
                        && request.plannedCruiseWorldZ() == 55.0
                        && request.includeAscend()
                        && !request.includeDescend())))
                .thenReturn(energyEstimate());
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan missionPlan = service.generateDirectPlan("mission-1");

        assertThat(missionPlan.getMission()).isSameAs(mission);
        assertThat(missionPlan.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.DIRECT);
        assertThat(missionPlan.getPlannedDistanceM()).isEqualTo(576.98);
        assertThat(missionPlan.getMaxPlannedAltitudeM()).isEqualTo(55.0);
        assertThat(missionPlan.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(missionPlan.getPlanningTimeMs()).isNotNegative();
        assertThat(missionPlan.getPlannedDurationSec()).isEqualTo(309.0);
        assertThat(missionPlan.getPlannedCruiseSpeedMps()).isEqualTo(2.0);
        assertThat(missionPlan.getEstimatedEnergyMah()).isEqualTo(480.0);
        assertThat(missionPlan.getEstimatedBatteryUsedPercent()).isEqualTo(9.6);
        assertThat(missionPlan.getSafetyReservePercent()).isEqualTo(20.0);
        assertThat(missionPlan.getRequiredBatteryPercent()).isEqualTo(29.6);
        assertThat(missionPlan.getAvailableBatteryPercentAtPlanning()).isNull();

        assertThat(missionPlan.getWaypoints()).hasSize(2);
        PlanWaypoint start = missionPlan.getWaypoints().get(0);
        assertWaypoint(start, missionPlan, 0, 0.0, -280.0, 55.0, WaypointReason.START);
        PlanWaypoint target = missionPlan.getWaypoints().get(1);
        assertWaypoint(target, missionPlan, 1, 260.0, 230.0, 55.0, WaypointReason.TARGET);
    }

    @Test
    void infeasibleDirectRoutePersistsPlanWithoutFakeWaypointsOrEnergyValues() {
        Mission mission = missionWithOrder(point(260.0, 230.0));
        PlannedRoute route = PlannedRoute.failed("Direct route intersects restricted zone.");
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.empty());
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(directRoutePlanner.plan(0.0, -280.0, 260.0, 230.0)).thenReturn(route);
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan missionPlan = service.generateDirectPlan("mission-1");

        assertThat(missionPlan.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.DIRECT);
        assertThat(missionPlan.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(missionPlan.getPlanningTimeMs()).isNotNegative();
        assertThat(missionPlan.getWaypoints()).isEmpty();
        assertThat(missionPlan.getPlannedDistanceM()).isZero();
        assertThat(missionPlan.getMaxPlannedAltitudeM()).isNull();
        assertNoEnergyValues(missionPlan);
        verifyNoInteractions(missionEnergyEstimator);
    }

    @Test
    void generateTwiceForSameMissionReusesPlanAndReplacesWaypoints() {
        Mission mission = missionWithOrder(point(260.0, 230.0));
        MissionPlan existingPlan = new MissionPlan();
        existingPlan.setMission(mission);
        existingPlan.getWaypoints().add(existingWaypoint(existingPlan, 0, 1.0, 2.0));
        existingPlan.getWaypoints().add(existingWaypoint(existingPlan, 1, 3.0, 4.0));
        existingPlan.getWaypoints().add(existingWaypoint(existingPlan, 2, 5.0, 6.0));

        PlannedRoute route = new PlannedRoute(
                true,
                100.0,
                40.0,
                List.of(
                        new PlannedRoute.RoutePoint(0.0, -280.0, 40.0),
                        new PlannedRoute.RoutePoint(100.0, 100.0, 40.0)),
                null);
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.of(existingPlan));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(planningEnvironment.sample(0.0, -280.0)).thenReturn(homeSample(10.0));
        when(directRoutePlanner.plan(0.0, -280.0, 260.0, 230.0)).thenReturn(route);
        when(missionEnergyEstimator.estimate(Mockito.any())).thenReturn(energyEstimate());
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan missionPlan = service.generateDirectPlan("mission-1");

        assertThat(missionPlan).isSameAs(existingPlan);
        assertThat(missionPlan.getWaypoints()).hasSize(2);
        assertThat(missionPlan.getWaypoints())
                .extracting(PlanWaypoint::getSequence)
                .containsExactly(0, 1);
        assertWaypoint(missionPlan.getWaypoints().get(0), missionPlan, 0, 0.0, -280.0, 40.0, WaypointReason.START);
        assertWaypoint(missionPlan.getWaypoints().get(1), missionPlan, 1, 100.0, 100.0, 40.0, WaypointReason.TARGET);
    }

    @Test
    void infeasibleReplanClearsStaleEnergyValuesAndWaypoints() {
        Mission mission = missionWithOrder(point(260.0, 230.0));
        MissionPlan existingPlan = new MissionPlan();
        existingPlan.setMission(mission);
        existingPlan.setPlannedDurationSec(10.0);
        existingPlan.setPlannedCruiseSpeedMps(2.0);
        existingPlan.setEstimatedEnergyMah(200.0);
        existingPlan.setEstimatedBatteryUsedPercent(4.0);
        existingPlan.setAvailableBatteryPercentAtPlanning(80.0);
        existingPlan.setSafetyReservePercent(20.0);
        existingPlan.setRequiredBatteryPercent(24.0);
        existingPlan.getWaypoints().add(existingWaypoint(existingPlan, 0, 1.0, 2.0));

        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.of(existingPlan));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(directRoutePlanner.plan(0.0, -280.0, 260.0, 230.0))
                .thenReturn(PlannedRoute.failed("Direct route leaves planning world."));
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan missionPlan = service.generateDirectPlan("mission-1");

        assertThat(missionPlan.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(missionPlan.getWaypoints()).isEmpty();
        assertNoEnergyValues(missionPlan);
        verifyNoInteractions(missionEnergyEstimator);
    }

    @Test
    void availableBatteryAtPlanningComesFromCurrentAssignedDroneTelemetryWhenPresent() {
        Mission mission = missionWithOrder(point(260.0, 230.0));
        mission.setId("mission-1");
        Drone drone = new Drone();
        drone.setDroneCode("DRONE-01");
        MissionDroneAssignment assignment = new MissionDroneAssignment();
        assignment.setMission(mission);
        assignment.setDrone(drone);
        DroneTelemetry telemetry = new DroneTelemetry();
        telemetry.setBatteryPercent(72.5);

        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.empty());
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(planningEnvironment.sample(0.0, -280.0)).thenReturn(homeSample(12.0));
        when(directRoutePlanner.plan(0.0, -280.0, 260.0, 230.0)).thenReturn(feasibleRoute());
        when(missionEnergyEstimator.estimate(Mockito.any())).thenReturn(energyEstimate());
        when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue("mission-1")).thenReturn(Optional.of(assignment));
        when(droneTelemetryRepository.findByDroneCode("DRONE-01")).thenReturn(Optional.of(telemetry));
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan missionPlan = service.generateDirectPlan("mission-1");

        assertThat(missionPlan.getAvailableBatteryPercentAtPlanning()).isEqualTo(72.5);
    }

    @Test
    void feasibleAStarPlanUsesAStarDistanceEnergyAndSimplifiedWaypoints() {
        Mission mission = missionWithOrder(point(260.0, 230.0));
        PlannedRoute route = new PlannedRoute(
                true,
                725.5,
                64.0,
                List.of(
                        new PlannedRoute.RoutePoint(0.0, -280.0, 64.0),
                        new PlannedRoute.RoutePoint(-20.0, -120.0, 64.0),
                        new PlannedRoute.RoutePoint(120.0, 40.0, 64.0),
                        new PlannedRoute.RoutePoint(260.0, 230.0, 64.0)),
                null);
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.empty());
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(planningEnvironment.sample(0.0, -280.0)).thenReturn(homeSample(9.4));
        when(aStarShortestRoutePlanner.plan(0.0, -280.0, 260.0, 230.0)).thenReturn(route);
        when(missionEnergyEstimator.estimate(Mockito.argThat(request ->
                request.plannedDistanceM() == 725.5
                        && request.homeWorldZ() == 9.4
                        && request.plannedCruiseWorldZ() == 64.0
                        && request.includeAscend()
                        && !request.includeDescend())))
                .thenReturn(energyEstimate());
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan missionPlan = service.generateAStarShortestPlan("mission-1");

        assertThat(missionPlan.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_SHORTEST);
        assertThat(missionPlan.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(missionPlan.getPlannedDistanceM()).isEqualTo(725.5);
        assertThat(missionPlan.getMaxPlannedAltitudeM()).isEqualTo(64.0);
        assertThat(missionPlan.getWaypoints()).hasSize(4);
        assertWaypoint(missionPlan.getWaypoints().get(0), missionPlan, 0, 0.0, -280.0, 64.0, WaypointReason.START);
        assertWaypoint(missionPlan.getWaypoints().get(1), missionPlan, 1, -20.0, -120.0, 64.0, WaypointReason.CRUISE);
        assertWaypoint(missionPlan.getWaypoints().get(2), missionPlan, 2, 120.0, 40.0, 64.0, WaypointReason.CRUISE);
        assertWaypoint(missionPlan.getWaypoints().get(3), missionPlan, 3, 260.0, 230.0, 64.0, WaypointReason.TARGET);
        verifyNoInteractions(directRoutePlanner);
    }

    @Test
    void directToAStarReplanReusesPlanAndReplacesRouteAndEnergy() {
        Mission mission = missionWithOrder(point(260.0, 230.0));
        MissionPlan existingPlan = new MissionPlan();
        existingPlan.setMission(mission);
        existingPlan.setPlanningAlgorithm(PlanningAlgorithm.DIRECT);
        existingPlan.setEstimatedEnergyMah(100.0);
        existingPlan.getWaypoints().add(existingWaypoint(existingPlan, 0, 0.0, -280.0));
        existingPlan.getWaypoints().add(existingWaypoint(existingPlan, 1, 260.0, 230.0));
        PlannedRoute route = new PlannedRoute(
                true,
                700.0,
                60.0,
                List.of(
                        new PlannedRoute.RoutePoint(0.0, -280.0, 60.0),
                        new PlannedRoute.RoutePoint(100.0, -100.0, 60.0),
                        new PlannedRoute.RoutePoint(260.0, 230.0, 60.0)),
                null);
        EnergyEstimate replacementEnergy = new EnergyEstimate(
                400.0, 350.0, 50.0, 0.0, 600.0, 12.0, 2.0, 5000.0, 20.0, 32.0);
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.of(existingPlan));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(planningEnvironment.sample(0.0, -280.0)).thenReturn(homeSample(10.0));
        when(aStarShortestRoutePlanner.plan(0.0, -280.0, 260.0, 230.0)).thenReturn(route);
        when(missionEnergyEstimator.estimate(Mockito.any())).thenReturn(replacementEnergy);
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan result = service.generateAStarShortestPlan("mission-1");

        assertThat(result).isSameAs(existingPlan);
        assertThat(result.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_SHORTEST);
        assertThat(result.getEstimatedEnergyMah()).isEqualTo(600.0);
        assertThat(result.getWaypoints()).hasSize(3);
        assertThat(result.getWaypoints()).extracting(PlanWaypoint::getReason)
                .containsExactly(WaypointReason.START, WaypointReason.CRUISE, WaypointReason.TARGET);
        verify(missionPlanRepository).flush();
    }

    @Test
    void infeasibleAStarReplanClearsAllStaleEnergyAndWaypoints() {
        Mission mission = missionWithOrder(point(260.0, 230.0));
        MissionPlan existingPlan = new MissionPlan();
        existingPlan.setMission(mission);
        existingPlan.setEstimatedEnergyMah(500.0);
        existingPlan.setPlannedDurationSec(300.0);
        existingPlan.setPlannedCruiseSpeedMps(2.0);
        existingPlan.setEstimatedBatteryUsedPercent(10.0);
        existingPlan.setAvailableBatteryPercentAtPlanning(75.0);
        existingPlan.setSafetyReservePercent(20.0);
        existingPlan.setRequiredBatteryPercent(30.0);
        existingPlan.getWaypoints().add(existingWaypoint(existingPlan, 0, 0.0, -280.0));
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.of(existingPlan));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(aStarShortestRoutePlanner.plan(0.0, -280.0, 260.0, 230.0))
                .thenReturn(PlannedRoute.failed("Target is restricted."));
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan result = service.generateAStarShortestPlan("mission-1");

        assertThat(result.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_SHORTEST);
        assertThat(result.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(result.getWaypoints()).isEmpty();
        assertNoEnergyValues(result);
        verifyNoInteractions(missionEnergyEstimator, directRoutePlanner);
    }

    @Test
    void generateAStarShortestPlanPreservesMissingMissionValidation() {
        when(missionRepository.findByIdWithOrder("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateAStarShortestPlan("missing"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission not found: missing");

        verifyNoInteractions(aStarShortestRoutePlanner, missionPlanRepository);
    }

    @Test
    void generateAStarEnergyAwarePlanPersistsSelectedRouteAndEstimatorResult() {
        Mission mission = missionWithOrder(point(200.0, -280.0));
        PlannedRoute route = new PlannedRoute(true, 218.526911934581, 19.5, List.of(
                new PlannedRoute.RoutePoint(0.0, -280.0, 19.5),
                new PlannedRoute.RoutePoint(50.0, -300.0, 19.5),
                new PlannedRoute.RoutePoint(200.0, -280.0, 19.5)), null);
        EnergyEstimate estimate = new EnergyEstimate(
                114.3634559672905, 109.2634559672905, 5.1, 0.0,
                187.971946616694, 3.75943893233388, 2.0, 5000.0, 20.0, 23.75943893233388);
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(aStarEnergyAwareRoutePlanner.plan(0.0, -280.0, 200.0, -280.0)).thenReturn(route);
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.empty());
        when(planningEnvironment.sample(0.0, -280.0)).thenReturn(homeSample(9.4));
        when(missionEnergyEstimator.estimate(Mockito.any())).thenReturn(estimate);
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan result = service.generateAStarEnergyAwarePlan("mission-1");

        assertThat(result.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(result.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(result.getPlannedDistanceM()).isEqualTo(route.distanceM());
        assertThat(result.getMaxPlannedAltitudeM()).isEqualTo(route.requiredWorldZM());
        assertThat(result.getEstimatedEnergyMah()).isEqualTo(estimate.estimatedEnergyMah());
        assertThat(result.getAvailableBatteryPercentAtPlanning()).isNull();
        assertThat(result.getWaypoints()).extracting(PlanWaypoint::getReason)
                .containsExactly(WaypointReason.START, WaypointReason.CRUISE, WaypointReason.TARGET);
        ArgumentCaptor<EnergyEstimateRequest> request = ArgumentCaptor.forClass(EnergyEstimateRequest.class);
        verify(missionEnergyEstimator).estimate(request.capture());
        assertThat(request.getValue().plannedDistanceM()).isEqualTo(route.distanceM());
        assertThat(request.getValue().plannedCruiseWorldZ()).isEqualTo(route.requiredWorldZM());
        assertThat(request.getValue().homeWorldZ()).isEqualTo(9.4);
        verifyNoInteractions(directRoutePlanner, aStarShortestRoutePlanner);
    }

    @Test
    void generateAStarEnergyAwarePlanReusesExistingPlanAndReplacesWaypoints() {
        Mission mission = missionWithOrder(point(200.0, -280.0));
        MissionPlan existing = new MissionPlan();
        existing.setMission(mission);
        existing.setPlanningAlgorithm(PlanningAlgorithm.ASTAR_SHORTEST);
        existing.setEstimatedEnergyMah(204.0);
        existing.getWaypoints().add(existingWaypoint(existing, 0, 0.0, -280.0));
        PlannedRoute route = feasibleRoute();
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(aStarEnergyAwareRoutePlanner.plan(0.0, -280.0, 200.0, -280.0)).thenReturn(route);
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.of(existing));
        when(planningEnvironment.sample(0.0, -280.0)).thenReturn(homeSample(9.4));
        when(missionEnergyEstimator.estimate(Mockito.any())).thenReturn(energyEstimate());
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan result = service.generateAStarEnergyAwarePlan("mission-1");

        assertThat(result).isSameAs(existing);
        assertThat(result.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(result.getWaypoints()).hasSize(2);
        verify(missionPlanRepository).flush();
    }

    @Test
    void infeasibleAStarEnergyAwareReplanClearsStaleStateWithoutEstimatingEnergy() {
        Mission mission = missionWithOrder(point(-200.0, -280.0));
        MissionPlan existing = new MissionPlan();
        existing.setMission(mission);
        existing.setPlannedDistanceM(218.5);
        existing.setMaxPlannedAltitudeM(19.5);
        existing.setEstimatedEnergyMah(187.9);
        existing.setPlannedDurationSec(114.3);
        existing.setPlannedCruiseSpeedMps(2.0);
        existing.setEstimatedBatteryUsedPercent(3.75);
        existing.setAvailableBatteryPercentAtPlanning(75.0);
        existing.setSafetyReservePercent(20.0);
        existing.setRequiredBatteryPercent(23.75);
        existing.getWaypoints().add(existingWaypoint(existing, 0, 0.0, -280.0));
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));
        when(simulationHomeProvider.home()).thenReturn(new SimulationPoint(0.0, -280.0));
        when(aStarEnergyAwareRoutePlanner.plan(0.0, -280.0, -200.0, -280.0))
                .thenReturn(PlannedRoute.failed("Target is inside restricted zone AIRPORT."));
        when(missionPlanRepository.findByMissionId("mission-1")).thenReturn(Optional.of(existing));
        when(missionPlanRepository.save(Mockito.any(MissionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionPlan result = service.generateAStarEnergyAwarePlan("mission-1");

        assertThat(result.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(result.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(result.getWaypoints()).isEmpty();
        assertNoEnergyValues(result);
        verifyNoInteractions(missionEnergyEstimator, directRoutePlanner, aStarShortestRoutePlanner);
    }

    @Test
    void generateAStarEnergyAwarePlanPreservesMissingMissionValidation() {
        when(missionRepository.findByIdWithOrder("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateAStarEnergyAwarePlan("missing"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission not found: missing");

        verifyNoInteractions(aStarEnergyAwareRoutePlanner, missionPlanRepository);
    }

    @Test
    void generateDirectPlanUsesExistingMissingMissionFailure() {
        when(missionRepository.findByIdWithOrder("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateDirectPlan("missing"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission not found: missing")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSION_NOT_FOUND);

        verifyNoInteractions(missionPlanRepository, directRoutePlanner);
    }

    @Test
    void generateDirectPlanFailsWhenMissionHasNoOrder() {
        Mission mission = new Mission();
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> service.generateDirectPlan("mission-1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission mission-1 has no order.");

        verifyNoInteractions(missionPlanRepository, directRoutePlanner);
    }

    @Test
    void generateDirectPlanFailsWhenOrderHasNoPoint() {
        when(missionRepository.findByIdWithOrder("mission-1")).thenReturn(Optional.of(missionWithOrder(null)));

        assertThatThrownBy(() -> service.generateDirectPlan("mission-1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Mission mission-1 order has no target point.");

        verifyNoInteractions(missionPlanRepository, directRoutePlanner);
    }

    private static Mission missionWithOrder(Point point) {
        Order order = new Order();
        order.setPoint(point);
        Mission mission = new Mission();
        mission.setOrder(order);
        return mission;
    }

    private static Point point(double x, double y) {
        return LOCAL_GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
    }

    private static PlannedRoute feasibleRoute() {
        return new PlannedRoute(
                true,
                100.0,
                20.0,
                List.of(
                        new PlannedRoute.RoutePoint(0.0, -280.0, 20.0),
                        new PlannedRoute.RoutePoint(260.0, 230.0, 20.0)),
                null);
    }

    private static EnergyEstimate energyEstimate() {
        return new EnergyEstimate(
                309.0,
                288.49,
                20.51,
                0.0,
                480.0,
                9.6,
                2.0,
                5000.0,
                20.0,
                29.6);
    }

    private static EnvironmentSample homeSample(double surfaceElevationM) {
        return new EnvironmentSample(0.0, -280.0, true, surfaceElevationM, 0.0, surfaceElevationM, false, null, null);
    }

    private static PlanWaypoint existingWaypoint(MissionPlan missionPlan, int sequence, double simX, double simY) {
        PlanWaypoint waypoint = new PlanWaypoint();
        waypoint.setMissionPlan(missionPlan);
        waypoint.setSequence(sequence);
        waypoint.setSimX(simX);
        waypoint.setSimY(simY);
        waypoint.setAltitudeM(10.0);
        waypoint.setReason(WaypointReason.CRUISE);
        return waypoint;
    }

    private static void assertWaypoint(
            PlanWaypoint waypoint,
            MissionPlan missionPlan,
            int sequence,
            double simX,
            double simY,
            double altitudeM,
            WaypointReason reason) {
        assertThat(waypoint.getMissionPlan()).isSameAs(missionPlan);
        assertThat(waypoint.getSequence()).isEqualTo(sequence);
        assertThat(waypoint.getSimX()).isEqualTo(simX);
        assertThat(waypoint.getSimY()).isEqualTo(simY);
        assertThat(waypoint.getAltitudeM()).isEqualTo(altitudeM);
        assertThat(waypoint.getPlannedSpeedMps()).isNull();
        assertThat(waypoint.getReason()).isEqualTo(reason);
    }

    private static void assertNoEnergyValues(MissionPlan missionPlan) {
        assertThat(missionPlan.getPlannedDurationSec()).isNull();
        assertThat(missionPlan.getPlannedCruiseSpeedMps()).isNull();
        assertThat(missionPlan.getEstimatedEnergyMah()).isNull();
        assertThat(missionPlan.getEstimatedBatteryUsedPercent()).isNull();
        assertThat(missionPlan.getAvailableBatteryPercentAtPlanning()).isNull();
        assertThat(missionPlan.getSafetyReservePercent()).isNull();
        assertThat(missionPlan.getRequiredBatteryPercent()).isNull();
    }
}
