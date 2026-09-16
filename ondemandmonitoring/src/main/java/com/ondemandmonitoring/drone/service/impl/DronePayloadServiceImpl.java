package com.ondemandmonitoring.drone.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.dto.request.DronePayloadCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DronePayloadUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DronePayloadResponse;
import com.ondemandmonitoring.drone.mapper.DronePayloadMapper;
import com.ondemandmonitoring.drone.repository.DronePayloadRepository;
import com.ondemandmonitoring.drone.service.IDronePayloadService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DronePayloadServiceImpl implements IDronePayloadService {

    DronePayloadRepository dronePayloadRepository;
    DronePayloadMapper dronePayloadMapper;

    @Override
    @Transactional
    public DronePayloadResponse create(DronePayloadCreateRequest request) {
        DronePayload dronePayload = dronePayloadMapper.toEntity(request);
        DronePayload saved = dronePayloadRepository.save(dronePayload);
        return dronePayloadMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DronePayloadResponse getById(String id) {
        DronePayload dronePayload = getEntityById(id);
        return dronePayloadMapper.toResponse(dronePayload);
    }

    @Override
    @Transactional(readOnly = true)
    public DronePayload getEntityById(String id) {
        return dronePayloadRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_PAYLOAD_NOT_FOUND,
                        "Drone payload not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DronePayloadResponse> getAll(Pageable pageable, String sensorType, String modelName) {
        Page<DronePayload> dronePayloads = dronePayloadRepository.searchDronePayloads(sensorType, modelName, pageable);
        Page<DronePayloadResponse> mappedPage = dronePayloads.map(dronePayloadMapper::toResponse);
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public DronePayloadResponse update(String id, DronePayloadUpdateRequest request) {
        DronePayload dronePayload = getEntityById(id);
        dronePayloadMapper.updateEntityFromRequest(request, dronePayload);
        DronePayload saved = dronePayloadRepository.save(dronePayload);
        return dronePayloadMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        DronePayload dronePayload = getEntityById(id);
        dronePayloadRepository.delete(dronePayload);
    }
}
