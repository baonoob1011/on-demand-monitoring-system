package com.ondemandmonitoring.chat.dto.response;


import com.ondemandmonitoring.chat.enums.ChatMessageType;

import java.time.Instant;

public record LastMessageResponse(

        String id,
        String senderId,
        String content,
        ChatMessageType messageType,
        Instant sentAt
) {
}