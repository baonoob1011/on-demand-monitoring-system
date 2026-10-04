package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.MissionChecklistExecution;
import com.ondemandmonitoring.mission.dto.response.ChecklistExecutionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MissionChecklistExecutionMapper {
    @Mapping(target = "content", source = "orderChecklistItem.content")
    @Mapping(target = "displayOrder", source = "orderChecklistItem.displayOrder")
    @Mapping(target = "sourceType", source = "orderChecklistItem.sourceType")
    ChecklistExecutionResponse toResponse(MissionChecklistExecution execution);
}
