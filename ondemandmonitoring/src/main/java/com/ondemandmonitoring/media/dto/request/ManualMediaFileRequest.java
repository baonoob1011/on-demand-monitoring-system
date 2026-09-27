package com.ondemandmonitoring.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/** Identifies an exact backup; mission and drone are never supplied by the client. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ManualMediaFileRequest {

    @NotNull
    @Positive
    Long fileSize;

    @NotBlank
    @Size(max = 100)
    String contentType;

    @NotBlank
    @Pattern(regexp = "[a-fA-F0-9]{64}")
    String checksumSha256;
}
