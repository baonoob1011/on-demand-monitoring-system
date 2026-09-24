package com.ondemandmonitoring.service.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.domain.ServiceDeliverable;
import com.ondemandmonitoring.service.dto.request.ServiceDeliverableRequest;
import com.ondemandmonitoring.service.dto.response.ServiceDeliverableResponse;
import com.ondemandmonitoring.service.mapper.ServiceDeliverableMapper;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.service.IDeliverableTypeService;
import com.ondemandmonitoring.service.service.IServiceDeliverableService;
import com.ondemandmonitoring.service.service.IServiceService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceDeliverableServiceImpl implements IServiceDeliverableService {

    ServiceDeliverableRepository serviceDeliverableRepository;
    ServiceDeliverableMapper serviceDeliverableMapper;
    IServiceService serviceService;
    IDeliverableTypeService deliverableTypeService;

    @Override
    @Transactional
    public ServiceDeliverableResponse create(ServiceDeliverableRequest request) {
        // Validate existence of both referenced entities
        Service service = serviceService.getEntityById(request.getServiceId());
        DeliverableType deliverableType = deliverableTypeService.getEntityById(request.getDeliverableTypeId());

        // Check for duplicate link
        if (serviceDeliverableRepository.existsByServiceIdAndDeliverableTypeId(
                request.getServiceId(), request.getDeliverableTypeId())) {
            throw new ApiException(ErrorCode.SERVICE_DELIVERABLE_ALREADY_EXISTS,
                    "This service-deliverable link already exists");
        }

        ServiceDeliverable entity = ServiceDeliverable.builder()
                .service(service)
                .deliverableType(deliverableType)
                .build();
        ServiceDeliverable saved = serviceDeliverableRepository.save(entity);
        return serviceDeliverableMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceDeliverableResponse> getAll() {
        return serviceDeliverableRepository.findAll().stream()
                .map(serviceDeliverableMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceDeliverableResponse> getByServiceId(String serviceId) {
        return serviceDeliverableRepository.findAllByServiceId(serviceId).stream()
                .map(serviceDeliverableMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceDeliverableResponse> getByDeliverableTypeId(String deliverableTypeId) {
        return serviceDeliverableRepository.findAllByDeliverableTypeId(deliverableTypeId).stream()
                .map(serviceDeliverableMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String id) {
        ServiceDeliverable entity = serviceDeliverableRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Service deliverable link not found with id: " + id));
        serviceDeliverableRepository.delete(entity);
    }
}
