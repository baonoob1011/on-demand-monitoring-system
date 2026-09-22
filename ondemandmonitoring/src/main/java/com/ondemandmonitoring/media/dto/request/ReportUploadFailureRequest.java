package com.ondemandmonitoring.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportUploadFailureRequest(@NotBlank @Size(max = 100) String code,
                                         @NotBlank @Size(max = 1000) String message) {
}
