package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Drone;
import com.ondemandmonitoring.device.domain.DroneModel;
import com.ondemandmonitoring.device.domain.DronePayload;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.device.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DroneResponse;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import com.ondemandmonitoring.device.mapper.DroneMapper;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.DroneModelRepository;
import com.ondemandmonitoring.device.repository.DronePayloadRepository;
import com.ondemandmonitoring.device.repository.DroneRepository;
import com.ondemandmonitoring.device.service.IDroneProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneProfileServiceImpl implements IDroneProfileService {

    DroneRepository droneRepository;
    DeviceRepository deviceRepository;
    DroneModelRepository droneModelRepository;
    DronePayloadRepository dronePayloadRepository;
    DroneMapper droneMapper;

    @Override
    @Transactional
    public DroneResponse create(DroneCreateRequest request) {
        if (droneRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Drone already exists with serial number: " + request.getSerialNumber());
        }

        DroneModel droneModel = droneModelRepository.findById(request.getDroneModelId())
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_MODEL_NOT_FOUND,
                        "Drone model not found with id: " + request.getDroneModelId()));

        DronePayload dronePayload = null;
        if (StringUtils.hasText(request.getDronePayloadId())) {
            dronePayload = dronePayloadRepository.findById(request.getDronePayloadId())
                    .orElseThrow(() -> new ApiException(ErrorCode.DRONE_PAYLOAD_NOT_FOUND,
                            "Drone payload not found with id: " + request.getDronePayloadId()));
        }

        Drone drone = droneMapper.toEntity(request);
        drone.setDroneModel(droneModel);
        drone.setDronePayload(dronePayload);
        linkDeviceProfile(drone, request.getDeviceId(), request.getSerialNumber(), request.getStatus());

        Drone saved = droneRepository.save(drone);
        return droneMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DroneResponse getById(String id) {
        Drone drone = getEntityById(id);
        return droneMapper.toResponse(drone);
    }

    @Override
    @Transactional(readOnly = true)
    public Drone getEntityById(String id) {
        return droneRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_NOT_FOUND,
                        "Drone not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Drone getEntityByCode(String code) {
        return droneRepository.findByDroneCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_NOT_FOUND));
    }

    @Override
    @Transactional
    public Drone getOrRegisterLegacySimulator(String code) {
        return droneRepository.findByDroneCode(code).orElseGet(() -> {
            Drone drone = new Drone();
            drone.setDroneCode(code);
            drone.setSerialNumber(code);
            drone.setDroneName("PX4 SITL Drone");
            drone.setStatus(DeviceOperationalStatus.AVAILABLE);
            drone.setLastSeenAt(LocalDateTime.now());
            linkDeviceProfile(drone, null, code, DeviceOperationalStatus.AVAILABLE);
            return droneRepository.save(drone);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DroneResponse> getAll(Pageable pageable, String modelId, String payloadId, DeviceOperationalStatus status) {
        Page<Drone> drones = droneRepository.searchDrones(modelId, payloadId, status, pageable);
        Page<DroneResponse> mappedPage = drones.map(droneMapper::toResponse);
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public DroneResponse update(String id, DroneUpdateRequest request) {
        Drone drone = getEntityById(id);

        if (droneRepository.existsBySerialNumberAndIdNot(request.getSerialNumber(), id)) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Drone already exists with serial number: " + request.getSerialNumber());
        }

        DroneModel droneModel = droneModelRepository.findById(request.getDroneModelId())
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_MODEL_NOT_FOUND,
                        "Drone model not found with id: " + request.getDroneModelId()));

        DronePayload dronePayload = null;
        if (StringUtils.hasText(request.getDronePayloadId())) {
            dronePayload = dronePayloadRepository.findById(request.getDronePayloadId())
                    .orElseThrow(() -> new ApiException(ErrorCode.DRONE_PAYLOAD_NOT_FOUND,
                            "Drone payload not found with id: " + request.getDronePayloadId()));
        }

        droneMapper.updateEntityFromRequest(request, drone);
        drone.setDroneModel(droneModel);
        drone.setDronePayload(dronePayload);
        linkDeviceProfile(drone, null, request.getSerialNumber(), request.getStatus());

        Drone saved = droneRepository.save(drone);
        return droneMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Drone drone = getEntityById(id);
        droneRepository.delete(drone);
    }

    private void linkDeviceProfile(Drone drone, String deviceId, String serialNumber, DeviceOperationalStatus operationalStatus) {
        Device device = null;
        if (StringUtils.hasText(deviceId)) {
            device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_FOUND,
                            "Device not found with id: " + deviceId));
        } else if (StringUtils.hasText(serialNumber)) {
            device = deviceRepository.findBySerialNumber(serialNumber).orElse(null);
        }
        if (device == null) {
            return;
        }

        final Device linkedDevice = device;
        droneRepository.findByDeviceId(linkedDevice.getId())
                .filter(existing -> !existing.getId().equals(drone.getId()))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Device already has a drone profile: " + linkedDevice.getSerialNumber());
                });

        drone.setDevice(linkedDevice);
        if (!StringUtils.hasText(drone.getSerialNumber())) {
            drone.setSerialNumber(linkedDevice.getSerialNumber());
        }
        if (!StringUtils.hasText(drone.getDroneCode())) {
            drone.setDroneCode(linkedDevice.getSerialNumber());
        }
        if (!StringUtils.hasText(drone.getDroneName())) {
            drone.setDroneName(linkedDevice.getName());
        }
        linkedDevice.setStatus(toDeviceStatus(operationalStatus));
        linkedDevice.setOperationalStatus(operationalStatus != null ? operationalStatus : DeviceOperationalStatus.AVAILABLE);
        deviceRepository.save(linkedDevice);
    }

    private DeviceStatus toDeviceStatus(DeviceOperationalStatus operationalStatus) {
        if (operationalStatus == DeviceOperationalStatus.MAINTENANCE) {
            return DeviceStatus.MAINTENANCE;
        }
        if (operationalStatus == DeviceOperationalStatus.AVAILABLE || operationalStatus == DeviceOperationalStatus.IDLE) {
            return DeviceStatus.AVAILABLE;
        }
        return DeviceStatus.IN_USE;
    }
}
