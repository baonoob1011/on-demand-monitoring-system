package com.ondemandmonitoring.checklist.service;

import com.ondemandmonitoring.checklist.dto.request.ChecklistAssignmentRequest;
import com.ondemandmonitoring.checklist.dto.response.ServiceChecklistResponse;

public interface IServiceChecklistService {
    ServiceChecklistResponse assign(String serviceId, ChecklistAssignmentRequest request);
    ServiceChecklistResponse updateOrder(String serviceId, String checklistId, int displayOrder);
    void unassign(String serviceId, String checklistId);
}
