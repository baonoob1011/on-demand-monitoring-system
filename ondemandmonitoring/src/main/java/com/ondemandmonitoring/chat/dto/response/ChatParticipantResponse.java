package com.ondemandmonitoring.chat.dto.response;

import java.time.Instant;

public record ChatParticipantResponse(

        String id,
        ChatUserResponse user,
        String lastReadMessageId,
        Instant lastReadAt,
        Instant joinedAt
) {
}