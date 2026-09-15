package com.ondemandmonitoring.media.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.media.storage-event-sqs")
public class MediaStorageEventSqsProperties {

    private boolean enabled;
    private String queueUrl;
    private int waitTimeSeconds = 20;
    private int visibilityTimeoutSeconds = 900;
    private int maxMessages = 10;
}
