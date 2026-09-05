package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.device.controller.response.DeviceImageResponse;
import com.ondemandmonitoring.device.controller.response.MediaResponse;
import com.ondemandmonitoring.device.domain.media.DeviceImage;
import com.ondemandmonitoring.device.service.DeviceImageService;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaController {

    DeviceImageService deviceImageService;

    @PostMapping(path = "/api/missions/{missionId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DeviceImageResponse>> uploadMissionImage(
            @PathVariable String missionId,
            @RequestParam("droneId") String droneId,
            @RequestParam("capturedAt") Instant capturedAt,
            @RequestPart("image") MultipartFile image) {
        DeviceImage uploaded = deviceImageService.upload(missionId, droneId, capturedAt, image);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Image uploaded", DeviceImageResponse.from(uploaded)));
    }

    @GetMapping("/api/media/{mediaId}")
    public ResponseEntity<ApiResponse<MediaResponse>> getMedia(@PathVariable String mediaId) {
        DeviceImage image = deviceImageService.getById(mediaId);
        String presignedUrl = deviceImageService.createPresignedGetUrl(image);

        return ResponseEntity.ok(ApiResponse.ok(MediaResponse.from(
                image,
                presignedUrl,
                deviceImageService.presignedUrlExpiresSeconds())));
    }
}
