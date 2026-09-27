package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.dto.response.MediaAssetResponse;
import com.ondemandmonitoring.media.dto.response.MediaResponse;
import com.ondemandmonitoring.media.mapper.MediaAssetMapper;
import com.ondemandmonitoring.media.service.IMediaAssetService;
import com.ondemandmonitoring.media.service.IMediaAssetService.MediaContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Drone Media", description = "APIs for retrieving and deleting drone images/videos stored on S3")
@RestController
@RequestMapping("/api/drones/{droneCode}/media")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneMediaController {

    IMediaAssetService mediaAssetService;
    MediaAssetMapper mediaAssetMapper;

    @Operation(summary = "List drone media", description = "Lists image/video metadata captured by a drone")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MediaAssetResponse>>> listDroneMedia(
            @PathVariable String droneCode,
            @RequestParam(required = false) String mediaType) {
        List<MediaAssetResponse> media = mediaAssetService.listByDrone(droneCode, mediaType)
                .stream()
                .map(mediaAssetMapper::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(media));
    }

    @Operation(summary = "Get drone media by ID", description = "Gets metadata and a presigned S3 URL for a drone image/video")
    @GetMapping("/{mediaId}")
    public ResponseEntity<ApiResponse<MediaResponse>> getDroneMedia(
            @PathVariable String droneCode,
            @PathVariable String mediaId) {
        MediaAsset mediaAsset = mediaAssetService.getByDroneAndId(droneCode, mediaId);
        String presignedUrl = mediaAssetService.createPresignedGetUrl(mediaAsset);

        return ResponseEntity.ok(ApiResponse.ok(mediaAssetMapper.toMediaResponse(
                mediaAsset,
                presignedUrl,
                mediaAssetService.presignedUrlExpiresSeconds())));
    }

    @Operation(summary = "Get drone media file", description = "Streams the original image/video file from S3 or local storage")
    @GetMapping("/{mediaId}/file")
    public ResponseEntity<InputStreamResource> getDroneMediaFile(
            @PathVariable String droneCode,
            @PathVariable String mediaId) {
        MediaAsset mediaAsset = mediaAssetService.getByDroneAndId(droneCode, mediaId);
        MediaContent mediaContent = mediaAssetService.openMedia(mediaAsset);
        MediaType contentType = MediaType.parseMediaType(mediaContent.contentType());

        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(mediaContent.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(mediaContent.fileName())
                        .build()
                        .toString())
                .body(new InputStreamResource(mediaContent.inputStream()));
    }

    @Operation(summary = "Delete drone media", description = "Deletes image/video metadata and the stored S3 object")
    @DeleteMapping("/{mediaId}")
    public ResponseEntity<ApiResponse<Void>> deleteDroneMedia(
            @PathVariable String droneCode,
            @PathVariable String mediaId) {
        mediaAssetService.deleteByDroneAndId(droneCode, mediaId);

        return ResponseEntity.ok(ApiResponse.ok("Media deleted", null));
    }
}
