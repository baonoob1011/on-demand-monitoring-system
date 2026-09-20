package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.dto.SimulationPoint;
import com.ondemandmonitoring.planning.service.SimulationHomeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredSimulationHomeProvider implements SimulationHomeProvider {

    static final double DRONE_BASE_CENTER_X = 0.0;
    static final double DRONE_BASE_CENTER_Y = -280.0;

    private final SimulationPoint home;

    public ConfiguredSimulationHomeProvider(
            @Value("${planning.simulation.home.x:" + DRONE_BASE_CENTER_X + "}") double homeX,
            @Value("${planning.simulation.home.y:" + DRONE_BASE_CENTER_Y + "}") double homeY) {
        this.home = new SimulationPoint(homeX, homeY);
    }

    @Override
    public SimulationPoint home() {
        return home;
    }
}
