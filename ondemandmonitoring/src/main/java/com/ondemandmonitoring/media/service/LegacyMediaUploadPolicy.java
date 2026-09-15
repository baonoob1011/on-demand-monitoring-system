package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LegacyMediaUploadPolicy {

    private final boolean enabled;

    public LegacyMediaUploadPolicy(
            @Value("${app.media.legacy-multipart-upload-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.GONE,
                    "Multipart media upload is disabled; use the reviewed presigned upload flow");
        }
    }
}
