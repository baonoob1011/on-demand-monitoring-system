package com.ondemandmonitoring.device.config;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceModel;
import com.ondemandmonitoring.device.domain.DeviceType;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceModelRepository;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.DeviceTypeRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@org.springframework.core.annotation.Order(35)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceSeedDataInitializer implements ApplicationRunner {

    static final String DRONE_TYPE_CODE = "DRONE";
    static final String CAMERA_TYPE_CODE = "CAMERA";
    static final String MODEL_CODE = "ODM-ASTAR-X1";

    DeviceTypeRepository deviceTypeRepository;
    DeviceModelRepository deviceModelRepository;
    DeviceRepository deviceRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        DeviceType droneType = ensureDeviceType(
                DRONE_TYPE_CODE,
                "Drone giám sát",
                "Thiết bị bay dùng cho mission giám sát.");
        DeviceType cameraType = ensureDeviceType(
                CAMERA_TYPE_CODE,
                "Camera onboard",
                "Camera gắn trên drone để ghi hình ảnh và video.");

        DeviceModel model = ensureDeviceModel(droneType, cameraType);

        ensureDevice("ODM-DRN-0052", "DRN-0052", "Drone test 0052", model, DeviceStatus.AVAILABLE);
        ensureDevice("ODM-DRN-0053", "DRN-0053", "Drone test 0053", model, DeviceStatus.AVAILABLE);
        ensureDevice("ODM-DRN-0054", "DRN-0054", "Drone test 0054", model, DeviceStatus.AVAILABLE);

        log.info("Device seed data ready for mission assignment testing.");
    }

    private DeviceType ensureDeviceType(String code, String name, String description) {
        return deviceTypeRepository.findByCode(code)
                .orElseGet(() -> {
                    DeviceType type = new DeviceType();
                    type.setCode(code);
                    type.setName(name);
                    type.setDescription(description);
                    return deviceTypeRepository.save(type);
                });
    }

    private DeviceModel ensureDeviceModel(DeviceType droneType, DeviceType cameraType) {
        return deviceModelRepository.findByCode(MODEL_CODE)
                .orElseGet(() -> {
                    DeviceModel model = new DeviceModel();
                    model.setCode(MODEL_CODE);
                    model.setManufacturer("OnDemand Monitor");
                    model.setModelName("A* Monitoring Drone X1");
                    model.setSpecsMetadata(Map.of(
                            "maxFlightMinutes", 35,
                            "camera", "RGB",
                            "supportsPreflight", true,
                            "supportsPostflight", true));
                    model.setDeviceTypes(new HashSet<>(Set.of(droneType, cameraType)));
                    return deviceModelRepository.save(model);
                });
    }

    private void ensureDevice(
            String serialNumber,
            String deviceCode,
            String name,
            DeviceModel model,
            DeviceStatus status) {
        deviceRepository.findBySerialNumber(serialNumber)
                .orElseGet(() -> {
                    Device device = new Device();
                    device.setSerialNumber(serialNumber);
                    device.setDeviceCode(deviceCode);
                    device.setName(name);
                    device.setDeviceModel(model);
                    device.setStatus(status);
                    device.setLastSeenAt(LocalDateTime.now());
                    return deviceRepository.save(device);
                });
    }
}
