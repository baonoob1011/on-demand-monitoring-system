package com.ondemandmonitoring.replanning.service;

import com.ondemandmonitoring.drone.domain.DeviceTelemetry;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.replanning.dto.ReplanningDecision;

public interface ReplanningPolicy {

    ReplanningDecision evaluate(DeviceTelemetry telemetry, MissionPlan currentPlan);
}
