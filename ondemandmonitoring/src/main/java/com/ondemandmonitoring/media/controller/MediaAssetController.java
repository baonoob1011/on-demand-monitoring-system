package com.ondemandmonitoring.media.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.media.dto.response.MediaAssetResponse;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.service.IMediaAssetService;
import com.ondemandmonitoring.media.mapper.MediaAssetMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Media & Assets", description = "APIs for uploading and retrieving images/media captured by drones")
@RestController
@RequestMapping("/api/devices/{deviceCode}/images")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaAssetController {

    IMediaAssetService mediaAssetService;
    MediaAssetMapper mediaAssetMapper;

    @Operation(summary = "Upload image for device", description = "Uploads a photo captured by a drone to storage")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaAssetResponse>> upload(
            @PathVariable String deviceCode,
            @RequestPart("file") MultipartFile file) {
        MediaAsset image = mediaAssetService.upload(deviceCode, file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Image uploaded", mediaAssetMapper.toResponse(image)));
    }
}
