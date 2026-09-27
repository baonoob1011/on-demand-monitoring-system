package com.ondemandmonitoring.support.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SupportMessageDto {
    private String id;
    private String ticketId;
    private String senderId;
    private String senderName;
    private String senderRole;
    private String content;
    private String attachmentUrl;
    private Instant createdAt;
}
