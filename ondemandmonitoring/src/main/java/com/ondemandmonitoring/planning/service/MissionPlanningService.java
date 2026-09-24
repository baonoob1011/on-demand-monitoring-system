package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.replanning.domain.ReplanningReason;

public interface MissionPlanningService {

    PlannedRoute validateDirectRoute(String missionId);

    MissionPlan generateDirectPlan(String missionId);

    MissionPlan generateAStarShortestPlan(String missionId);

    MissionPlan generateAStarEnergyAwarePlan(String missionId);

    MissionPlan replanAStarEnergyAwareFromCurrentPosition(
            String missionId,
            double currentSimX,
            double currentSimY,
            ReplanningReason reason);
}
