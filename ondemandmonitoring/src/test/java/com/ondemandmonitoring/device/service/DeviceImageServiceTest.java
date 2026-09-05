package com.ondemandmonitoring.device.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ondemandmonitoring.device.infrastructure.s3.AwsS3Properties;
import com.ondemandmonitoring.device.repository.DeviceImageRepository;
import com.ondemandmonitoring.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class DeviceImageServiceTest {

    @Test
    void upload_rejectsMissingFile() {
        DeviceImageService service = new DeviceImageService(
                mock(S3Client.class), mock(S3Presigner.class), new AwsS3Properties(), mock(DeviceImageRepository.class));

        assertThatThrownBy(() -> service.upload("DRONE-01", null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Image file is required");
    }

    @Test
    void upload_rejectsUnsupportedContentType() {
        DeviceImageService service = new DeviceImageService(
                mock(S3Client.class), mock(S3Presigner.class), new AwsS3Properties(), mock(DeviceImageRepository.class));
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "capture.txt", "text/plain", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.upload("DRONE-01", file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only PNG and JPEG images are allowed");
    }
}
