package com.ondemandmonitoring.drone.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.DroneModel;
import com.ondemandmonitoring.drone.dto.request.DroneModelCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneModelUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneModelResponse;
import com.ondemandmonitoring.drone.mapper.DroneModelMapper;
import com.ondemandmonitoring.drone.repository.DroneModelRepository;
import com.ondemandmonitoring.drone.service.IDroneModelService;
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
public class DroneModelServiceImpl implements IDroneModelService {

    DroneModelRepository droneModelRepository;
    DroneModelMapper droneModelMapper;

    @Override
    @Transactional
    public DroneModelResponse create(DroneModelCreateRequest request) {
        DroneModel droneModel = droneModelMapper.toEntity(request);
        DroneModel saved = droneModelRepository.save(droneModel);
        return droneModelMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DroneModelResponse getById(String id) {
        DroneModel droneModel = getEntityById(id);
        return droneModelMapper.toResponse(droneModel);
    }

    @Override
    @Transactional(readOnly = true)
    public DroneModel getEntityById(String id) {
        return droneModelRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_MODEL_NOT_FOUND,
                        "Drone model not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DroneModelResponse> getAll(Pageable pageable, String category, String manufacturer) {
        Page<DroneModel> droneModels = droneModelRepository.searchDroneModels(category, manufacturer, pageable);
        Page<DroneModelResponse> mappedPage = droneModels.map(droneModelMapper::toResponse);
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public DroneModelResponse update(String id, DroneModelUpdateRequest request) {
        DroneModel droneModel = getEntityById(id);
        droneModelMapper.updateEntityFromRequest(request, droneModel);
        DroneModel saved = droneModelRepository.save(droneModel);
        return droneModelMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        DroneModel droneModel = getEntityById(id);
        droneModelRepository.delete(droneModel);
    }
}
