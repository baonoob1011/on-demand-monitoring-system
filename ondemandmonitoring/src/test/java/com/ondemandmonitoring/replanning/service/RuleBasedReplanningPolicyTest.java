package com.ondemandmonitoring.replanning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.replanning.config.ReplanningProperties;
import com.ondemandmonitoring.replanning.domain.ReplanningReason;
import com.ondemandmonitoring.replanning.dto.ReplanningDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RuleBasedReplanningPolicyTest {

    private RuleBasedReplanningPolicy policy;

    @BeforeEach
    void setUp() {
        ReplanningProperties properties = new ReplanningProperties();
        ReflectionTestUtils.setField(properties, "enabled", true);
        ReflectionTestUtils.setField(properties, "routeDeviationThresholdMeters", 20.0);
        ReflectionTestUtils.setField(properties, "minimumBatteryReservePercent", 20.0);
        policy = new RuleBasedReplanningPolicy(properties);
    }

    @Test
    void normalTelemetryNearRouteDoesNotTriggerReplan() {
        ReplanningDecision decision = policy.evaluate(telemetry(50.0, 3.0, 85.0), plan(18.0, 20.0));

        assertThat(decision.required()).isFalse();
    }

    @Test
    void routeDeviationBeyondThresholdTriggersReplan() {
        ReplanningDecision decision = policy.evaluate(telemetry(50.0, 35.0, 85.0), plan(18.0, 20.0));

        assertThat(decision.required()).isTrue();
        assertThat(decision.reason()).isEqualTo(ReplanningReason.ROUTE_DEVIATION);
    }

    @Test
    void lowBatteryAgainstCurrentPlanTriggersRemainingRouteInfeasible() {
        ReplanningDecision decision = policy.evaluate(telemetry(50.0, 3.0, 30.0), plan(18.0, 20.0));

        assertThat(decision.required()).isTrue();
        assertThat(decision.reason()).isEqualTo(ReplanningReason.REMAINING_ROUTE_INFEASIBLE);
    }

    @Test
    void telemetryWithoutSimulationPositionDoesNotGenerateFakeRoute() {
        DroneTelemetry telemetry = telemetry(null, null, 90.0);

        ReplanningDecision decision = policy.evaluate(telemetry, plan(18.0, 20.0));

        assertThat(decision.required()).isFalse();
    }

    private MissionPlan plan(double estimatedBatteryUsedPercent, double safetyReservePercent) {
        MissionPlan plan = new MissionPlan();
        plan.setEstimatedBatteryUsedPercent(estimatedBatteryUsedPercent);
        plan.setSafetyReservePercent(safetyReservePercent);
        plan.getWaypoints().add(waypoint(0, 0.0, 0.0));
        plan.getWaypoints().add(waypoint(1, 100.0, 0.0));
        return plan;
    }

    private PlanWaypoint waypoint(int sequence, double simX, double simY) {
        PlanWaypoint waypoint = new PlanWaypoint();
        waypoint.setSequence(sequence);
        waypoint.setSimX(simX);
        waypoint.setSimY(simY);
        return waypoint;
    }

    private DroneTelemetry telemetry(Double simX, Double simY, Double batteryPercent) {
        DroneTelemetry telemetry = new DroneTelemetry();
        telemetry.setSimX(simX);
        telemetry.setSimY(simY);
        telemetry.setBatteryPercent(batteryPercent);
        return telemetry;
    }
}
