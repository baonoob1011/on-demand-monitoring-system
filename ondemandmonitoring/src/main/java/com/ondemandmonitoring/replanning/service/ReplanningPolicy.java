package com.ondemandmonitoring.replanning.service;

import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.replanning.dto.ReplanningDecision;

public interface ReplanningPolicy {

    ReplanningDecision evaluate(DroneTelemetry telemetry, MissionPlan currentPlan);
}
