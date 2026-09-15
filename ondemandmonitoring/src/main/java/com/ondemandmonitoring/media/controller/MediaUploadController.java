package com.ondemandmonitoring.media.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.media.dto.MediaUploadResponse;
import com.ondemandmonitoring.media.dto.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.dto.ReportUploadFailureRequest;
import com.ondemandmonitoring.media.service.IMediaUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'SYSTEM_OPERATOR', 'ADMIN')")
public class MediaUploadController {

    private final IMediaUploadService mediaUploadService;

    @PostMapping("/missions/{missionId}/media-uploads")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> prepare(
            @PathVariable String missionId,
            @Valid @RequestBody PrepareMediaUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Media upload prepared", mediaUploadService.prepare(missionId, request)));
    }

    @PostMapping("/media/{mediaId}/upload-attempts")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> retry(@PathVariable String mediaId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Media upload retry prepared", mediaUploadService.retry(mediaId)));
    }

    @PostMapping("/media/{mediaId}/manual-upload-attempts")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> prepareManualUpload(
            @PathVariable String mediaId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        "Manual media upload prepared",
                        mediaUploadService.prepareManualUpload(mediaId)));
    }

    @PostMapping("/media/{mediaId}/upload-attempts/{attemptId}/failures")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> reportFailure(
            @PathVariable String mediaId,
            @PathVariable String attemptId,
            @Valid @RequestBody ReportUploadFailureRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Media upload failure recorded",
                mediaUploadService.reportFailure(mediaId, attemptId, request)));
    }

    @PostMapping("/media/{mediaId}/upload-attempts/{attemptId}/uploaded")
    public ResponseEntity<ApiResponse<Void>> markUploaded(
            @PathVariable String mediaId,
            @PathVariable String attemptId) {
        mediaUploadService.markUploaded(mediaId, attemptId);
        return ResponseEntity.ok(ApiResponse.ok("Upload acknowledgement recorded", null));
    }

    @GetMapping("/media/{mediaId}/upload-status")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> status(@PathVariable String mediaId) {
        return ResponseEntity.ok(ApiResponse.ok(mediaUploadService.getStatus(mediaId)));
    }
}
