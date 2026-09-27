package com.ondemandmonitoring.support.dto;

import lombok.Data;

@Data
public class UpdateTicketStatusRequest {
    private String status; // OPEN, ASSIGNED, IN_PROGRESS, WAITING_FOR_CUSTOMER, WAITING_FOR_STAFF, RESOLVED, CLOSED, CANCELLED
    private String assignedStaffId;
    private String assignedStaffName;
    private String priority; // NORMAL, HIGH, URGENT
    private String resolutionNotes;
}
