package com.ondemandmonitoring.drone.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneResponse;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import org.springframework.data.domain.Pageable;

public interface IDroneService {

    DroneResponse create(DroneCreateRequest request);

    DroneResponse getById(String id);

    Drone getEntityById(String id);

    PageResponse<DroneResponse> getAll(Pageable pageable, String modelId, String payloadId, DroneStatus status);

    DroneResponse update(String id, DroneUpdateRequest request);

    void delete(String id);
}
