package com.ondemandmonitoring.replanning.service;

import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.replanning.domain.ReplanningReason;

public interface MissionReplanningService {

    MissionPlan replanFromTelemetry(String missionId, DroneTelemetry telemetry, ReplanningReason reason);
}
