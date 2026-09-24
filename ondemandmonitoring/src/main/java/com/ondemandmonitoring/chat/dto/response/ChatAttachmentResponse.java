package com.ondemandmonitoring.chat.dto.response;


import com.ondemandmonitoring.chat.enums.ChatAttachmentType;

public record ChatAttachmentResponse(
        String id,
        String fileUrl,
        String fileName,
        String contentType,
        Long fileSize,
        ChatAttachmentType attachmentType
) {
}