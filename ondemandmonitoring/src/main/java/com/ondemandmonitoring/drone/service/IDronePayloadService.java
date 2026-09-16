package com.ondemandmonitoring.drone.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.dto.request.DronePayloadCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DronePayloadUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DronePayloadResponse;
import org.springframework.data.domain.Pageable;

public interface IDronePayloadService {

    DronePayloadResponse create(DronePayloadCreateRequest request);

    DronePayloadResponse getById(String id);

    DronePayload getEntityById(String id);

    PageResponse<DronePayloadResponse> getAll(Pageable pageable, String sensorType, String modelName);

    DronePayloadResponse update(String id, DronePayloadUpdateRequest request);

    void delete(String id);
}
