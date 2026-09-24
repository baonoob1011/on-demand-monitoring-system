package com.ondemandmonitoring.service.service;

import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.dto.request.ServiceRequest;
import com.ondemandmonitoring.service.dto.response.ServiceResponse;
import java.util.List;

/**
 * Service interface for managing monitoring services.
 */
public interface IServiceService {

    ServiceResponse create(ServiceRequest request);

    ServiceResponse getById(String id);

    List<ServiceResponse> getAll();

    List<ServiceResponse> getAllActive();

    ServiceResponse update(String id, ServiceRequest request);

    void delete(String id);

    Service getEntityById(String id);
}
