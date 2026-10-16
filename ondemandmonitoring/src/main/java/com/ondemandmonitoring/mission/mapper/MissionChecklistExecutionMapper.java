package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.MissionChecklistExecution;
import com.ondemandmonitoring.mission.dto.response.ChecklistExecutionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MissionChecklistExecutionMapper {
    @Mapping(target = "content", source = "orderChecklistItem.content")
    @Mapping(target = "displayOrder", source = "missionChecklistAssignment.displayOrder")
    @Mapping(target = "sourceType", source = "orderChecklistItem.sourceType")
    @Mapping(target = "evidencePolicyVersion", source = "orderChecklistItem.evidencePolicyVersion")
    @Mapping(target = "minimumEvidenceCount", source = "orderChecklistItem.minimumEvidenceCount")
    @Mapping(target = "requiredEvidenceType", source = "orderChecklistItem.requiredEvidenceType")
    @Mapping(target = "minimumImageCount", source = "orderChecklistItem.minimumImageCount")
    @Mapping(target = "minimumVideoCount", source = "orderChecklistItem.minimumVideoCount")
    ChecklistExecutionResponse toResponse(MissionChecklistExecution execution);
}
