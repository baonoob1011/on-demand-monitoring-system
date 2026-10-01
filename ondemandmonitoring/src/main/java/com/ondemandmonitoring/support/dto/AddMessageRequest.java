package com.ondemandmonitoring.support.dto;

import lombok.Data;

@Data
public class AddMessageRequest {
    private String senderId;
    private String senderName;
    private String senderRole; // CUSTOMER, STAFF, MANAGER, SYSTEM
    private String content;
    private String attachmentUrl;
}
