package com.ondemandmonitoring.categoryservice.service;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.dto.request.CategoryServiceRequest;
import com.ondemandmonitoring.categoryservice.dto.response.CategoryServiceResponse;
import java.util.List;

/**
 * Service interface for managing category services.
 */
public interface ICategoryServiceService {

    CategoryServiceResponse create(CategoryServiceRequest request);

    CategoryServiceResponse getById(String id);

    List<CategoryServiceResponse> getAll();

    CategoryServiceResponse update(String id, CategoryServiceRequest request);

    void delete(String id);

    boolean existsById(String id);

    CategoryService getEntityById(String id);
}
