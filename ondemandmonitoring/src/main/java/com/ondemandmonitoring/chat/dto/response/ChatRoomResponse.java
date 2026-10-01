package com.ondemandmonitoring.chat.dto.response;


import com.ondemandmonitoring.chat.enums.ChatRoomType;

import java.time.Instant;
import java.util.List;

public record ChatRoomResponse(

        String id,

        ChatRoomType type,

        List<ChatParticipantResponse> participants,

        LastMessageResponse lastMessage,

        long unreadCount,

        Instant lastMessageAt,

        Instant createdAt
) {
}