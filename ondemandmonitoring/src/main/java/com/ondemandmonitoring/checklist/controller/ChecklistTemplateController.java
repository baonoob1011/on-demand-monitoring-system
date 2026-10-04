package com.ondemandmonitoring.checklist.controller;

import com.ondemandmonitoring.checklist.dto.response.ServiceChecklistResponse;
import com.ondemandmonitoring.checklist.service.IServiceChecklistService;
import com.ondemandmonitoring.common.api.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ChecklistTemplateController {
    private final IServiceChecklistService service;

    @GetMapping("/api/services/{serviceId}/checklists")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ServiceChecklistResponse>> template(@PathVariable String serviceId) {
        return ApiResponse.ok(service.getByService(serviceId, true));
    }

    @GetMapping("/api/admin/checklists/{checklistId}/services")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ServiceChecklistResponse>> services(@PathVariable String checklistId) {
        return ApiResponse.ok(service.getByChecklist(checklistId));
    }
}
