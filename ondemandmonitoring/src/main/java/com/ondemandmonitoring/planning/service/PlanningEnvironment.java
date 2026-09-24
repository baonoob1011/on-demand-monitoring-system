package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.domain.PlanningGrid;

public interface PlanningEnvironment {

    EnvironmentSample sample(double simX, double simY);

    PlanningGrid grid();

    default boolean isInsideWorldBounds(double simX, double simY) {
        return sample(simX, simY).insideWorldBounds();
    }

    default boolean isRestricted(double simX, double simY) {
        return sample(simX, simY).restricted();
    }
}
