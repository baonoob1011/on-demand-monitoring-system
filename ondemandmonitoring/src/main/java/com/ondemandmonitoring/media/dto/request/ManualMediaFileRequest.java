package com.ondemandmonitoring.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Identifies an exact backup; mission and drone are never supplied by the client. */
public record ManualMediaFileRequest(
        @NotNull @Positive Long fileSize,
        @NotBlank @Size(max = 100) String contentType,
        @NotBlank @Pattern(regexp = "(?i)[a-f0-9]{64}") String checksumSha256) {
}
