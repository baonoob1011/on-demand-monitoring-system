package com.ondemandmonitoring.categoryservice.service.impl;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.dto.request.CategoryServiceRequest;
import com.ondemandmonitoring.categoryservice.dto.response.CategoryServiceResponse;
import com.ondemandmonitoring.categoryservice.mapper.CategoryServiceMapper;
import com.ondemandmonitoring.categoryservice.repository.CategoryServiceRepository;
import com.ondemandmonitoring.categoryservice.service.ICategoryServiceService;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryServiceServiceImpl implements ICategoryServiceService {

    CategoryServiceRepository categoryServiceRepository;
    CategoryServiceMapper categoryServiceMapper;

    @Override
    @Transactional
    public CategoryServiceResponse create(CategoryServiceRequest request) {
        CategoryService entity = categoryServiceMapper.toEntity(request);
        CategoryService saved = categoryServiceRepository.save(entity);
        return categoryServiceMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryServiceResponse getById(String id) {
        CategoryService entity = getEntityById(id);
        return categoryServiceMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryServiceResponse> getAll() {
        return categoryServiceRepository.findAll().stream()
                .map(categoryServiceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryServiceResponse update(String id, CategoryServiceRequest request) {
        CategoryService entity = getEntityById(id);
        categoryServiceMapper.updateEntityFromRequest(request, entity);
        CategoryService saved = categoryServiceRepository.save(entity);
        return categoryServiceMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        CategoryService entity = getEntityById(id);
        categoryServiceRepository.delete(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(String id) {
        return categoryServiceRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryService getEntityById(String id) {
        return categoryServiceRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Category service not found with id: " + id));
    }
}
