package com.ondemandmonitoring.service.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.dto.request.DeliverableTypeRequest;
import com.ondemandmonitoring.service.dto.response.DeliverableTypeResponse;
import com.ondemandmonitoring.service.mapper.DeliverableTypeMapper;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.service.IDeliverableTypeService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeliverableTypeServiceImpl implements IDeliverableTypeService {

    DeliverableTypeRepository deliverableTypeRepository;
    DeliverableTypeMapper deliverableTypeMapper;

    @Override
    @Transactional
    public DeliverableTypeResponse create(DeliverableTypeRequest request) {
        if (deliverableTypeRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ApiException(ErrorCode.DELIVERABLE_TYPE_ALREADY_EXISTS,
                    "Deliverable type with name '" + request.getName() + "' already exists");
        }
        DeliverableType entity = deliverableTypeMapper.toEntity(request);
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        DeliverableType saved = deliverableTypeRepository.save(entity);
        return deliverableTypeMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliverableTypeResponse getById(String id) {
        DeliverableType entity = getEntityById(id);
        return deliverableTypeMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliverableTypeResponse> getAll() {
        return deliverableTypeRepository.findAll().stream()
                .map(deliverableTypeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliverableTypeResponse> getAllActive() {
        return deliverableTypeRepository.findAllByIsActiveTrue().stream()
                .map(deliverableTypeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DeliverableTypeResponse update(String id, DeliverableTypeRequest request) {
        DeliverableType entity = getEntityById(id);
        deliverableTypeMapper.updateEntityFromRequest(request, entity);
        DeliverableType saved = deliverableTypeRepository.save(entity);
        return deliverableTypeMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        DeliverableType entity = getEntityById(id);
        entity.setIsActive(false);
        deliverableTypeRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliverableType getEntityById(String id) {
        return deliverableTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DELIVERABLE_TYPE_NOT_FOUND,
                        "Deliverable type not found with id: " + id));
    }
}
