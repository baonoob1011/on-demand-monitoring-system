package com.ondemandmonitoring.drone.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.domain.DroneModel;
import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneResponse;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.mapper.DroneMapper;
import com.ondemandmonitoring.drone.repository.DroneModelRepository;
import com.ondemandmonitoring.drone.repository.DronePayloadRepository;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.drone.service.IDroneService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneServiceImpl implements IDroneService {

    DroneRepository droneRepository;
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
    public PageResponse<DroneResponse> getAll(Pageable pageable, String modelId, String payloadId, DroneStatus status) {
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

        Drone saved = droneRepository.save(drone);
        return droneMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Drone drone = getEntityById(id);
        droneRepository.delete(drone);
    }
}
