package com.ondemandmonitoring.chat.dto.response;


import com.ondemandmonitoring.chat.enums.ChatMessageType;

import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(

        String id,

        String chatRoomId,

        ChatUserResponse sender,

        ChatMessageType messageType,

        String content,

        ReplyMessageResponse replyTo,

        List<ChatAttachmentResponse> attachments,

        Instant sentAt,

        Instant editedAt,

        Instant deletedAt
) {
}