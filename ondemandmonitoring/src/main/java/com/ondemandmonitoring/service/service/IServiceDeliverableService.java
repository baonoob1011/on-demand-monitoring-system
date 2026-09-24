package com.ondemandmonitoring.service.service;

import com.ondemandmonitoring.service.dto.request.ServiceDeliverableRequest;
import com.ondemandmonitoring.service.dto.response.ServiceDeliverableResponse;
import java.util.List;

/**
 * Service interface for managing service-deliverable type relationships.
 */
public interface IServiceDeliverableService {

    ServiceDeliverableResponse create(ServiceDeliverableRequest request);

    List<ServiceDeliverableResponse> getAll();

    List<ServiceDeliverableResponse> getByServiceId(String serviceId);

    List<ServiceDeliverableResponse> getByDeliverableTypeId(String deliverableTypeId);

    void delete(String id);
}
