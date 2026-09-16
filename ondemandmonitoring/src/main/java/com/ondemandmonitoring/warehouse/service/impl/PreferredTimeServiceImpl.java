package com.ondemandmonitoring.warehouse.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.dto.request.PreferredTimeCreateRequest;
import com.ondemandmonitoring.warehouse.dto.request.PreferredTimeUpdateRequest;
import com.ondemandmonitoring.warehouse.dto.response.PreferredTimeResponse;
import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import com.ondemandmonitoring.warehouse.mapper.PreferredTimeMapper;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import com.ondemandmonitoring.warehouse.service.IPreferredTimeService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PreferredTimeServiceImpl implements IPreferredTimeService {

    PreferredTimeRepository preferredTimeRepository;
    PreferredTimeMapper preferredTimeMapper;

    @Override
    @Transactional
    public PreferredTimeResponse create(PreferredTimeCreateRequest request) {
        if (preferredTimeRepository.existsByCode(request.getCode())) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "PreferredTime already exists with code: " + request.getCode());
        }

        PreferredTime entity = preferredTimeMapper.toEntity(request);
        PreferredTime savedEntity = preferredTimeRepository.save(entity);
        return preferredTimeMapper.toResponse(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public PreferredTimeResponse getById(String id) {
        PreferredTime entity = preferredTimeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "PreferredTime not found with id: " + id));
        return preferredTimeMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PreferredTimeResponse getByCode(PreferredTimeCode code) {
        PreferredTime entity = preferredTimeRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "PreferredTime not found with code: " + code));
        return preferredTimeMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PreferredTimeResponse> getAll() {
        return preferredTimeRepository.findAll().stream()
                .map(preferredTimeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PreferredTimeResponse update(String id, PreferredTimeUpdateRequest request) {
        PreferredTime entity = preferredTimeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "PreferredTime not found with id: " + id));

        if (request.getCode() != null && !request.getCode().equals(entity.getCode())
                && preferredTimeRepository.existsByCode(request.getCode())) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "PreferredTime already exists with code: " + request.getCode());
        }

        preferredTimeMapper.updateEntityFromRequest(request, entity);
        PreferredTime updatedEntity = preferredTimeRepository.save(entity);
        return preferredTimeMapper.toResponse(updatedEntity);
    }

    @Override
    @Transactional
    public void delete(String id) {
        PreferredTime entity = preferredTimeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "PreferredTime not found with id: " + id));
        preferredTimeRepository.delete(entity);
    }
}
