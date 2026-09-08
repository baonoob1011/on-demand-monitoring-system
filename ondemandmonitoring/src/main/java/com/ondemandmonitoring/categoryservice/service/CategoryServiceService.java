package com.ondemandmonitoring.categoryservice.service;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.dto.request.CategoryServiceRequest;
import com.ondemandmonitoring.categoryservice.dto.response.CategoryServiceResponse;
import com.ondemandmonitoring.categoryservice.mapper.CategoryServiceMapper;
import com.ondemandmonitoring.categoryservice.repository.CategoryServiceRepository;
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
public class CategoryServiceService {

    CategoryServiceRepository categoryServiceRepository;
    CategoryServiceMapper categoryServiceMapper;

    @Transactional
    public CategoryServiceResponse create(CategoryServiceRequest request) {
        CategoryService entity = categoryServiceMapper.toEntity(request);
        CategoryService saved = categoryServiceRepository.save(entity);
        return categoryServiceMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CategoryServiceResponse getById(Long id) {
        CategoryService entity = getEntityById(id);
        return categoryServiceMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CategoryServiceResponse> getAll() {
        return categoryServiceRepository.findAll().stream()
                .map(categoryServiceMapper::toResponse)
                .toList();
    }

    @Transactional
    public CategoryServiceResponse update(Long id, CategoryServiceRequest request) {
        CategoryService entity = getEntityById(id);
        categoryServiceMapper.updateEntityFromRequest(request, entity);
        CategoryService saved = categoryServiceRepository.save(entity);
        return categoryServiceMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        CategoryService entity = getEntityById(id);
        categoryServiceRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return categoryServiceRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public CategoryService getEntityById(Long id) {
        return categoryServiceRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Category service not found with id: " + id));
    }
}
