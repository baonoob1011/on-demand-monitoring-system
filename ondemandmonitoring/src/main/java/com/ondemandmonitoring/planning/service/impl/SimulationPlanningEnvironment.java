package com.ondemandmonitoring.planning.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Optional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulationPlanningEnvironment implements PlanningEnvironment {

    private static final String PLANNING_MAP_RESOURCE = "planning/forest_monitoring_compact-planning-map.json";
    private static final int SRID = 0;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), SRID);

    private final ZoneRepository zoneRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlanningGrid planningGrid;

    @Autowired
    public SimulationPlanningEnvironment(ZoneRepository zoneRepository) {
        this(zoneRepository, null);
    }

    public SimulationPlanningEnvironment(
            ZoneRepository zoneRepository,
            PlanningGrid planningGrid) {
        this.zoneRepository = zoneRepository;
        this.planningGrid = planningGrid;
    }

    @PostConstruct
    void loadPlanningGrid() throws IOException {
        if (planningGrid != null) {
            return;
        }

        ClassPathResource resource = new ClassPathResource(PLANNING_MAP_RESOURCE);
        try (var inputStream = resource.getInputStream()) {
            planningGrid = objectMapper.readValue(inputStream, PlanningGrid.class);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EnvironmentSample sample(double simX, double simY) {
        PlanningGrid.GridSample gridSample = planningGrid.sample(simX, simY);
        Optional<Zone> restrictedZone = findRestrictedZone(simX, simY);

        return new EnvironmentSample(
                simX,
                simY,
                planningGrid.contains(simX, simY),
                gridSample.terrainElevationM(),
                gridSample.obstacleHeightM(),
                gridSample.surfaceElevationM(),
                restrictedZone.isPresent(),
                restrictedZone.map(Zone::getCode).orElse(null),
                restrictedZone.map(Zone::getName).orElse(null));
    }

    @Override
    public PlanningGrid grid() {
        return planningGrid;
    }

    private Optional<Zone> findRestrictedZone(double simX, double simY) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(simX, simY));
        point.setSRID(SRID);

        return zoneRepository.findAllByRestrictedTrueOrderByCodeAsc().stream()
                .filter(Zone::isRestricted)
                .filter(this::isCurrentSimulationZone)
                .filter(zone -> zone.getPolygon() != null)
                .filter(zone -> zone.getPolygon().covers(point))
                .findFirst();
    }

    private boolean isCurrentSimulationZone(Zone zone) {
        return planningGrid.world().equals(zone.getSourceWorld())
                && planningGrid.coordinateSystem().equals(zone.getCoordinateSystem());
    }
}
