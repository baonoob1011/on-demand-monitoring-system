package com.ondemandmonitoring.chat.dto.response;

public record ChatTypingResponse(
        String chatRoomId,
        String userId,
        boolean typing
) {
}