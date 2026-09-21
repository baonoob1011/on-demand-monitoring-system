package com.ondemandmonitoring.service.service;

import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.dto.request.DeliverableTypeRequest;
import com.ondemandmonitoring.service.dto.response.DeliverableTypeResponse;
import java.util.List;

/**
 * Service interface for managing deliverable types.
 */
public interface IDeliverableTypeService {

    DeliverableTypeResponse create(DeliverableTypeRequest request);

    DeliverableTypeResponse getById(String id);

    List<DeliverableTypeResponse> getAll();

    List<DeliverableTypeResponse> getAllActive();

    DeliverableTypeResponse update(String id, DeliverableTypeRequest request);

    void delete(String id);

    DeliverableType getEntityById(String id);
}
