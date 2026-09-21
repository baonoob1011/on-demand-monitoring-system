package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.domain.PlanningGrid;

public interface PlanningEnvironment {

    EnvironmentSample sample(double simX, double simY);

    PlanningGrid grid();

    /**
     * Returns a stable view for one planning operation. Implementations backed by
     * mutable external data can snapshot that data once instead of reloading it per cell.
     */
    default PlanningEnvironment snapshot() {
        return this;
    }

    default boolean isInsideWorldBounds(double simX, double simY) {
        return sample(simX, simY).insideWorldBounds();
    }

    default boolean isRestricted(double simX, double simY) {
        return sample(simX, simY).restricted();
    }
}
