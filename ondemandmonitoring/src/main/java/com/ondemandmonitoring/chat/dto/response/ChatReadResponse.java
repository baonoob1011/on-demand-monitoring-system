package com.ondemandmonitoring.chat.dto.response;

import java.time.Instant;

public record ChatReadResponse(
        String chatRoomId,
        String userId,
        String lastReadMessageId,
        Instant readAt
) {
}