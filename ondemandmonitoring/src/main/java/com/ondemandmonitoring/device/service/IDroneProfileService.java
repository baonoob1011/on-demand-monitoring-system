package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.device.domain.Drone;
import com.ondemandmonitoring.device.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.device.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DroneResponse;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import org.springframework.data.domain.Pageable;

public interface IDroneProfileService {

    DroneResponse create(DroneCreateRequest request);

    DroneResponse getById(String id);

    Drone getEntityById(String id);

    Drone getEntityByCode(String code);

    /** Compatibility registration for the legacy simulator upload flow only. */
    Drone getOrRegisterLegacySimulator(String code);

    PageResponse<DroneResponse> getAll(Pageable pageable, String modelId, String payloadId, DeviceOperationalStatus status);

    DroneResponse update(String id, DroneUpdateRequest request);

    void delete(String id);
}
