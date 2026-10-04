package com.ondemandmonitoring.checklist.service.impl;

import com.ondemandmonitoring.checklist.domain.*;
import com.ondemandmonitoring.checklist.dto.request.ChecklistAssignmentRequest;
import com.ondemandmonitoring.checklist.dto.response.ServiceChecklistResponse;
import com.ondemandmonitoring.checklist.mapper.ServiceChecklistMapper;
import com.ondemandmonitoring.checklist.repository.*;
import com.ondemandmonitoring.checklist.service.IServiceChecklistService;
import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional
public class ServiceChecklistServiceImpl implements IServiceChecklistService {
    private final ServiceRepository services;
    private final ChecklistDefinitionRepository checklists;
    private final ServiceChecklistRepository assignments;
    private final ServiceChecklistMapper mapper;

    @Override
    public ServiceChecklistResponse assign(String serviceId, ChecklistAssignmentRequest request) {
        validateOrder(request.getDisplayOrder());
        Service service = lockService(serviceId);
        ChecklistDefinition checklist = checklists.findByIdForUpdate(request.getChecklistId())
                .orElseThrow(() -> new ApiException(ErrorCode.CHECKLIST_NOT_FOUND));
        if (!Boolean.TRUE.equals(service.getIsActive())) throw new ApiException(ErrorCode.SERVICE_INACTIVE);
        if (!Boolean.TRUE.equals(checklist.getIsActive())) throw new ApiException(ErrorCode.CHECKLIST_INACTIVE);
        if (assignments.existsByServiceIdAndChecklistId(serviceId, checklist.getId())) {
            throw new ApiException(ErrorCode.SERVICE_CHECKLIST_ALREADY_EXISTS);
        }
        ServiceChecklist entity = new ServiceChecklist();
        entity.setService(service);
        entity.setChecklist(checklist);
        entity.setDisplayOrder(request.getDisplayOrder());
        return mapper.toResponse(assignments.saveAndFlush(entity));
    }

    @Override
    public ServiceChecklistResponse updateOrder(String serviceId, String checklistId, int displayOrder) {
        validateOrder(displayOrder);
        lockService(serviceId);
        ServiceChecklist entity = assignment(serviceId, checklistId);
        entity.setDisplayOrder(displayOrder);
        return mapper.toResponse(assignments.saveAndFlush(entity));
    }

    @Override
    public void unassign(String serviceId, String checklistId) {
        lockService(serviceId);
        assignments.delete(assignment(serviceId, checklistId));
        assignments.flush();
    }

    private Service lockService(String id) {
        return services.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceChecklistResponse> getByService(String serviceId, boolean activeOnly) {
        Service service = services.findById(serviceId)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND));
        if (activeOnly && !Boolean.TRUE.equals(service.getIsActive())) {
            throw new ApiException(ErrorCode.SERVICE_INACTIVE);
        }
        List<ServiceChecklist> rows = activeOnly
                ? assignments.findAllByServiceIdAndChecklistIsActiveTrueOrderByDisplayOrderAscIdAsc(serviceId)
                : assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc(serviceId);
        return rows.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceChecklistResponse> getByChecklist(String checklistId) {
        if (!checklists.existsById(checklistId)) throw new ApiException(ErrorCode.CHECKLIST_NOT_FOUND);
        return assignments.findAllByChecklistIdOrderByServiceNameAscServiceIdAsc(checklistId)
                .stream().map(mapper::toResponse).toList();
    }

    private ServiceChecklist assignment(String serviceId, String checklistId) {
        return assignments.findByServiceIdAndChecklistId(serviceId, checklistId)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_CHECKLIST_NOT_FOUND));
    }

    private void validateOrder(Integer order) {
        if (order == null || order < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Display order must be non-negative");
        }
    }
}
