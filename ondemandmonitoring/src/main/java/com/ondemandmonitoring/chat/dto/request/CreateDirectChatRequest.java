package com.ondemandmonitoring.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateDirectChatRequest(

        @NotNull
        String participantUserId
) {
}