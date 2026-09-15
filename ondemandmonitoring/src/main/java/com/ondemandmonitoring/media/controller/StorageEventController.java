package com.ondemandmonitoring.media.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.media.dto.S3ObjectCreatedRequest;
import com.ondemandmonitoring.media.event.StorageObjectCreatedEvent;
import com.ondemandmonitoring.media.service.MediaUploadService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/storage-events")
@RequiredArgsConstructor
public class StorageEventController {

    private final MediaUploadService mediaUploadService;

    @Value("${app.media.storage-event-secret:}")
    private String configuredSecret;

    @PostMapping("/s3-object-created")
    public ResponseEntity<ApiResponse<Void>> objectCreated(
            @RequestHeader(name = "X-Storage-Event-Secret", required = false) String suppliedSecret,
            @Valid @RequestBody S3ObjectCreatedRequest request) {
        if (configuredSecret.isBlank() || suppliedSecret == null || !MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.MEDIA_STORAGE_EVENT_UNAUTHORIZED);
        }
        mediaUploadService.processObjectCreated(new StorageObjectCreatedEvent(
                request.bucket(), request.key(), request.size(), request.eventId(), request.versionId(),
                request.sequencer(), request.eventName(), null));
        return ResponseEntity.ok(ApiResponse.ok("Storage event processed", null));
    }
}
