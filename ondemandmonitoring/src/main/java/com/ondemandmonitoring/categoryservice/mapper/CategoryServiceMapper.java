package com.ondemandmonitoring.categoryservice.mapper;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.dto.request.CategoryServiceRequest;
import com.ondemandmonitoring.categoryservice.dto.response.CategoryServiceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryServiceMapper {

    @Mapping(target = "id", ignore = true)
    CategoryService toEntity(CategoryServiceRequest request);

    CategoryServiceResponse toResponse(CategoryService entity);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(CategoryServiceRequest request, @MappingTarget CategoryService entity);
}
