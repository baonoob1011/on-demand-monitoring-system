package com.ondemandmonitoring.media.dto;

import com.ondemandmonitoring.media.dto.request.ManualMediaFileRequest;
import com.ondemandmonitoring.media.dto.response.ManualMediaUploadResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ManualMediaDtoTest {
    @Test
    void manualDtosFollowClassAndBeanConventions() {
        assertThat(ManualMediaFileRequest.class.isRecord()).isFalse();
        assertThat(ManualMediaUploadResponse.class.isRecord()).isFalse();
        var request = new ManualMediaFileRequest();
        request.setFileSize(100L);
        request.setContentType("image/jpeg");
        request.setChecksumSha256("a".repeat(64));
        assertThat(request.getFileSize()).isEqualTo(100L);
        assertThat(request.getContentType()).isEqualTo("image/jpeg");
        var response = ManualMediaUploadResponse.builder().backendMediaId("media-id").build();
        assertThat(response.getBackendMediaId()).isEqualTo("media-id");
    }

    @Test
    void manualRequestRetainsValidationConstraints() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new ManualMediaFileRequest())).hasSize(3);
            assertThat(validator.validate(ManualMediaFileRequest.builder()
                    .fileSize(100L).contentType("image/jpeg").checksumSha256("A".repeat(64)).build())).isEmpty();
            assertThat(validator.validate(new ManualMediaFileRequest(0L, "image/jpeg", "invalid"))).hasSize(2);
        }
    }
}
