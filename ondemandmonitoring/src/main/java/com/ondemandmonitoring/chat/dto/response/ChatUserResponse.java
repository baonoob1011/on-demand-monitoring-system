package com.ondemandmonitoring.chat.dto.response;

public record ChatUserResponse(

        String id,
        String fullName,
        String avatarUrl
) {
}