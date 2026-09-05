package com.ondemandmonitoring.controllers;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.dto.response.DeviceImageResponse;
import com.ondemandmonitoring.entities.DeviceImage;
import com.ondemandmonitoring.services.DeviceImageService;
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

@RestController
@RequestMapping("/api/devices/{deviceCode}/images")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceImageController {

    DeviceImageService deviceImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DeviceImageResponse>> upload(
            @PathVariable String deviceCode,
            @RequestPart("file") MultipartFile file) {
        DeviceImage image = deviceImageService.upload(deviceCode, file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Image uploaded", DeviceImageResponse.from(image)));
    }
}
