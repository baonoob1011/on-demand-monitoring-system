package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.planning.dto.PlannedRoute;

public interface MissionPlanningService {

    PlannedRoute validateDirectRoute(String missionId);

    MissionPlan generateDirectPlan(String missionId);

    MissionPlan generateAStarShortestPlan(String missionId);

    MissionPlan generateAStarEnergyAwarePlan(String missionId);
}
