package com.ondemandmonitoring.drone.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.drone.domain.DroneModel;
import com.ondemandmonitoring.drone.dto.request.DroneModelCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneModelUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneModelResponse;
import org.springframework.data.domain.Pageable;

public interface IDroneModelService {

    DroneModelResponse create(DroneModelCreateRequest request);

    DroneModelResponse getById(String id);

    DroneModel getEntityById(String id);

    PageResponse<DroneModelResponse> getAll(Pageable pageable, String category, String manufacturer);

    DroneModelResponse update(String id, DroneModelUpdateRequest request);

    void delete(String id);
}
