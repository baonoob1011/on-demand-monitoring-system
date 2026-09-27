package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.device.domain.DroneModel;
import com.ondemandmonitoring.device.dto.request.DroneModelCreateRequest;
import com.ondemandmonitoring.device.dto.request.DroneModelUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DroneModelResponse;
import org.springframework.data.domain.Pageable;

public interface IDroneModelService {

    DroneModelResponse create(DroneModelCreateRequest request);

    DroneModelResponse getById(String id);

    DroneModel getEntityById(String id);

    PageResponse<DroneModelResponse> getAll(Pageable pageable, String category, String manufacturer);

    DroneModelResponse update(String id, DroneModelUpdateRequest request);

    void delete(String id);
}
