package com.ondemandmonitoring.media.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CompleteMultipartRequest(@NotEmpty List<@Valid Part> parts) {
    public record Part(@Min(1) int partNumber, @NotBlank String eTag) {}
}
