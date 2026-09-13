package com.ondemandmonitoring.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record PrepareMediaUploadRequest(
        @NotBlank @Size(max = 50) String droneId,
        @NotBlank @Size(max = 100) String localMediaId,
        @NotBlank @Pattern(regexp = "IMAGE|VIDEO") String mediaType,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Pattern(regexp = "image/jpeg|image/png|video/mp4") String contentType,
        @NotNull @Positive Long fileSize,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String checksumSha256,
        @NotNull @PastOrPresent Instant capturedAt) {
}
