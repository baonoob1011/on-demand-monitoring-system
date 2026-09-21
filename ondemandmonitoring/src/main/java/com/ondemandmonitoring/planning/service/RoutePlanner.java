package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlannedRoute;

public interface RoutePlanner {

    PlannedRoute plan(
            double startX,
            double startY,
            double targetX,
            double targetY);
}
