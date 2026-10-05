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
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ondemandmonitoring.checklist.dto.request.ChecklistReorderRequest;
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
        var rows = new ArrayList<>(assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc(serviceId));
        if (rows.size() >= 100) throw new ApiException(ErrorCode.INVALID_REQUEST, "At most 100 template items are allowed");
        rows.add(Math.min(request.getDisplayOrder(), rows.size()), entity);
        normalize(rows);
        service.setChecklistDefaultsInitialized(true);
        return mapper.toResponse(assignments.saveAndFlush(entity));
    }

    @Override
    public ServiceChecklistResponse updateOrder(String serviceId, String checklistId, int displayOrder) {
        validateOrder(displayOrder);
        Service service = lockService(serviceId);
        ServiceChecklist entity = assignment(serviceId, checklistId);
        var rows = new ArrayList<>(assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc(serviceId));
        rows.removeIf(row -> checklistId.equals(row.getChecklist().getId()));
        rows.add(Math.min(displayOrder, rows.size()), entity);
        normalize(rows);
        service.setChecklistDefaultsInitialized(true);
        return mapper.toResponse(assignments.saveAndFlush(entity));
    }

    @Override
    public void unassign(String serviceId, String checklistId) {
        Service service = lockService(serviceId);
        assignments.delete(assignment(serviceId, checklistId));
        assignments.flush();
        normalize(assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc(serviceId));
        service.setChecklistDefaultsInitialized(true);
    }

    @Override
    public List<ServiceChecklistResponse> reorder(String serviceId, ChecklistReorderRequest request) {
        Service service = lockService(serviceId);
        var rows = assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc(serviceId);
        if (request == null || request.items() == null || request.items().size() != rows.size()
                || rows.size() > 100 || request.items().stream().anyMatch(item -> item == null || item.checklistId() == null))
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Reorder must include every assigned checklist once");
        var byId = rows.stream().collect(Collectors.toMap(row -> row.getChecklist().getId(), Function.identity()));
        var ordered = new ArrayList<ServiceChecklist>();
        for (var item : request.items()) {
            var row = byId.remove(item.checklistId());
            if (row == null) throw new ApiException(ErrorCode.INVALID_REQUEST, "Duplicate or unknown checklist");
            if (!Objects.equals(row.getVersion(), item.expectedVersion()) || item.expectedVersion() == null)
                throw new ApiException(ErrorCode.CONCURRENT_UPDATE);
            ordered.add(row);
        }
        normalize(ordered);
        service.setChecklistDefaultsInitialized(true);
        return ordered.stream().map(mapper::toResponse).toList();
    }

    private void normalize(List<ServiceChecklist> rows) {
        for (int i = 0; i < rows.size(); i++) rows.get(i).setDisplayOrder(i);
        assignments.saveAllAndFlush(rows);
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
