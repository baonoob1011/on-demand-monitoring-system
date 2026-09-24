package com.ondemandmonitoring.chat.dto.request;

import com.ondemandmonitoring.chat.enums.ChatMessageType;
import jakarta.validation.constraints.NotNull;

public record SendChatMessageRequest(

        String content,

        @NotNull
        ChatMessageType messageType,

        String replyToMessageId
) {
}