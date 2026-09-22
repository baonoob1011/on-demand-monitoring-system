package com.ondemandmonitoring.media.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.media.dto.*;
import com.ondemandmonitoring.media.service.IMediaUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'SYSTEM_OPERATOR', 'ADMIN')")
public class MediaUploadController {
    private final IMediaUploadService uploads;

    @PostMapping("/missions/{missionId}/media-uploads")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> prepare(
            @PathVariable String missionId, @Valid @RequestBody PrepareMediaUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(uploads.prepare(missionId, request)));
    }

    @GetMapping("/media/{mediaId}/upload-status")
    public ApiResponse<MediaUploadResponse> status(@PathVariable String mediaId) {
        return ApiResponse.ok(uploads.status(mediaId));
    }

    @PostMapping("/media/{mediaId}/upload-attempts")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> retry(@PathVariable String mediaId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(uploads.retry(mediaId, false)));
    }

    @PostMapping("/media/{mediaId}/manual-upload-attempts")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> manual(@PathVariable String mediaId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(uploads.retry(mediaId, true)));
    }

    @PostMapping("/media/{mediaId}/upload-attempts/{attemptId}/parts/{partNumber}/url")
    public ApiResponse<MediaUploadResponse> partUrl(@PathVariable String mediaId,
                                                    @PathVariable String attemptId,
                                                    @PathVariable int partNumber) {
        return ApiResponse.ok(uploads.presignPart(mediaId, attemptId, partNumber));
    }

    @PostMapping("/media/{mediaId}/upload-attempts/{attemptId}/complete-multipart")
    public ApiResponse<Void> completeMultipart(@PathVariable String mediaId, @PathVariable String attemptId,
                                               @Valid @RequestBody CompleteMultipartRequest request) {
        uploads.completeMultipart(mediaId, attemptId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/media/{mediaId}/upload-attempts/{attemptId}/uploaded")
    public ApiResponse<Void> uploaded(@PathVariable String mediaId, @PathVariable String attemptId) {
        uploads.markUploaded(mediaId, attemptId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/media/{mediaId}/upload-attempts/{attemptId}/failures")
    public ApiResponse<MediaUploadResponse> failure(@PathVariable String mediaId, @PathVariable String attemptId,
                                                     @Valid @RequestBody ReportUploadFailureRequest request) {
        return ApiResponse.ok(uploads.reportFailure(mediaId, attemptId, request));
    }
}
