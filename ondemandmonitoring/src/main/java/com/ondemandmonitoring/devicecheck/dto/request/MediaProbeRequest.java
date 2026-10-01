package com.ondemandmonitoring.devicecheck.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class MediaProbeRequest {

    @NotNull
    MultipartFile file;

    @NotBlank
    @Pattern(regexp = "[a-fA-F0-9]{64}")
    String checksumSha256;
}
