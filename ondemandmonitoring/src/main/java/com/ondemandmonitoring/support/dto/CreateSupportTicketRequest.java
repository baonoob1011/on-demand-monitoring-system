package com.ondemandmonitoring.support.dto;

import lombok.Data;

@Data
public class CreateSupportTicketRequest {
    private String customerId;
    private String customerName;
    private String orderId;
    private String missionId;
    private String category;
    private String subject;
    private String description;
    private String priority; // NORMAL, HIGH, URGENT
    private String attachmentUrl;
}
