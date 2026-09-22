package com.ondemandmonitoring.media.dto;

import java.time.Instant;

public record CustomerMediaNotificationResponse(String notificationId, String mediaId,
                                                String missionId, String eventType, Instant createdAt) {
}
