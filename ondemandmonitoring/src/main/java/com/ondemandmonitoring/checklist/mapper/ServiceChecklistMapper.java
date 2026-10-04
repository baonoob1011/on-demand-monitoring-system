package com.ondemandmonitoring.checklist.mapper;

import com.ondemandmonitoring.checklist.domain.ServiceChecklist;
import com.ondemandmonitoring.checklist.dto.response.ServiceChecklistResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ServiceChecklistMapper {
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceName", source = "service.name")
    @Mapping(target = "serviceActive", source = "service.isActive")
    @Mapping(target = "checklistId", source = "checklist.id")
    @Mapping(target = "content", source = "checklist.content")
    @Mapping(target = "checklistActive", source = "checklist.isActive")
    ServiceChecklistResponse toResponse(ServiceChecklist entity);
}
