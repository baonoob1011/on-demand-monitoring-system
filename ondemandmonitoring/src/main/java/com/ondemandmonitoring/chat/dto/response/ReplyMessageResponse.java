package com.ondemandmonitoring.chat.dto.response;

public record ReplyMessageResponse(

        String id,
        String senderId,
        String senderName,
        String content
) {
}