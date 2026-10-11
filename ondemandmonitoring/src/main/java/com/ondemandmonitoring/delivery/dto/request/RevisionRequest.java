package com.ondemandmonitoring.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Customer feedback requesting a new revision of the released preview. */
public record RevisionRequest(@NotBlank @Size(max = 2000) String reason, List<String> mediaAssetIds) {}
