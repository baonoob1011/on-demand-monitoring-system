package com.ondemandmonitoring.drone.config;

import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.domain.DroneModel;
import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.repository.DroneModelRepository;
import com.ondemandmonitoring.drone.repository.DronePayloadRepository;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seed.demo-drones", havingValue = "true")
public class DroneSeedDataInitializer implements ApplicationRunner {

    private static final String MODEL_CODE = "X500-SITL";
    private static final List<SeedDrone> SEED_DRONES = List.of(
            new SeedDrone("DRN-0048", "Eagle-48", "Front RGB + Thermal", "RGB_THERMAL"),
            new SeedDrone("DRN-0049", "Eagle-49", "Downward RGB Camera", "RGB"),
            new SeedDrone("DRN-0050", "Eagle-50", "Front RGB Zoom Camera", "RGB_ZOOM"),
            new SeedDrone("DRN-0051", "Eagle-51", "Multispectral RGB Camera", "RGB_MULTISPECTRAL"),
            new SeedDrone("DRN-0052", "Eagle-52", "Thermal + RGB Camera", "RGB_THERMAL")
    );

    private final DroneModelRepository droneModelRepository;
    private final DronePayloadRepository dronePayloadRepository;
    private final DroneRepository droneRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SeedDrone> missingDrones = new ArrayList<>();
        for (SeedDrone seed : SEED_DRONES) {
            if (droneRepository.findByDroneCode(seed.code()).isPresent()) {
                continue;
            }
            String serialNumber = "SIM-X500-" + seed.code();
            if (droneRepository.existsBySerialNumber(serialNumber)) {
                log.warn("Skipping demo drone {}: serial {} is already registered", seed.code(), serialNumber);
                continue;
            }
            missingDrones.add(seed);
        }
        if (missingDrones.isEmpty()) {
            return;
        }

        DroneModel model = droneModelRepository.findByModelCode(MODEL_CODE)
                .orElseGet(() -> droneModelRepository.save(createModel()));
        for (SeedDrone seed : missingDrones) {
            DronePayload payload = dronePayloadRepository.findByModelNameIgnoreCase(seed.cameraName())
                    .orElseGet(() -> dronePayloadRepository.save(createCameraPayload(seed)));

            Drone drone = new Drone();
            drone.setDroneCode(seed.code());
            drone.setDroneName(seed.name());
            drone.setSerialNumber("SIM-X500-" + seed.code());
            drone.setDroneModel(model);
            drone.setDronePayload(payload);
            drone.setStatus(DroneStatus.AVAILABLE);
            droneRepository.save(drone);
        }
    }

    private DroneModel createModel() {
        DroneModel model = new DroneModel();
        model.setModelCode(MODEL_CODE);
        model.setManufacturer("PX4");
        model.setCategory("Simulation");
        model.setMaxFlightTimeMinutes(45.0);
        model.setMaxTakeoffWeightKg(2.5);
        model.setMaxFlightAltitudeMeters(120.0);
        model.setMaxWindResistanceMetersPerSecond(12.0);
        model.setIpRating("SIM");
        model.setSpecsMetadata("{}");
        return model;
    }

    private DronePayload createCameraPayload(SeedDrone seed) {
        DronePayload payload = new DronePayload();
        payload.setModelName(seed.cameraName());
        payload.setSensorType(seed.sensorType());
        payload.setWeightKg(0.45);
        payload.setPayloadCapabilities("FPV, image capture, video capture");
        return payload;
    }

    private record SeedDrone(String code, String name, String cameraName, String sensorType) {
    }
}
