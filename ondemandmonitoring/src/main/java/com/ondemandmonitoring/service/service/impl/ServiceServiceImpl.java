package com.ondemandmonitoring.service.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.dto.request.ServiceRequest;
import com.ondemandmonitoring.service.dto.response.ServiceResponse;
import com.ondemandmonitoring.service.mapper.ServiceMapper;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.service.service.IServiceService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceServiceImpl implements IServiceService {

    ServiceRepository serviceRepository;
    ServiceMapper serviceMapper;

    @Override
    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        if (serviceRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ApiException(ErrorCode.SERVICE_ALREADY_EXISTS,
                    "Service with name '" + request.getName() + "' already exists");
        }
        Service entity = serviceMapper.toEntity(request);
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        Service saved = serviceRepository.save(entity);
        return serviceMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResponse getById(String id) {
        Service entity = getEntityById(id);
        return serviceMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> getAll() {
        return serviceRepository.findAll().stream()
                .map(serviceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> getAllActive() {
        return serviceRepository.findAllByIsActiveTrue().stream()
                .map(serviceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ServiceResponse update(String id, ServiceRequest request) {
        Service entity = getEntityById(id);
        serviceMapper.updateEntityFromRequest(request, entity);
        Service saved = serviceRepository.save(entity);
        return serviceMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Service entity = getEntityById(id);
        entity.setIsActive(false);
        serviceRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Service getEntityById(String id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND,
                        "Service not found with id: " + id));
    }
}
