package com.ondemandmonitoring.device.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.s3.AwsS3Properties;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceImage;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.DeviceImageRepository;
import com.ondemandmonitoring.common.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.multipart.MultipartFile;

class DeviceImageServiceTest {

    @Test
    void upload_rejectsMissingFile() {
        DeviceImageService service = new DeviceImageService(
                mock(S3ObjectStorageService.class),
                new AwsS3Properties(),
                mock(Environment.class),
                mock(DeviceRepository.class),
                mock(DeviceImageRepository.class));

        assertThatThrownBy(() -> service.upload("DRONE-01", null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Media file is required");
    }

    @Test
    void upload_rejectsUnsupportedContentType() {
        DeviceImageService service = new DeviceImageService(
                mock(S3ObjectStorageService.class),
                new AwsS3Properties(),
                mock(Environment.class),
                mock(DeviceRepository.class),
                mock(DeviceImageRepository.class));
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "capture.txt", "text/plain", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.upload("DRONE-01", file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only PNG, JPEG, and MP4 media are allowed");
    }

    @Test
    void upload_rejectsMismatchedVideoMediaType() {
        DeviceImageService service = new DeviceImageService(
                mock(S3ObjectStorageService.class),
                new AwsS3Properties(),
                mock(Environment.class),
                mock(DeviceRepository.class),
                mock(DeviceImageRepository.class));
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "clip.mp4", "video/mp4", "fake-mp4".getBytes());

        assertThatThrownBy(() -> service.upload("MISSION_001", "DRONE-01", null, file, "IMAGE"))
                .isInstanceOf(ApiException.class)
                .hasMessage("mediaType does not match uploaded content type");
    }

    @Test
    void upload_acceptsVideoMp4AsLocalMedia() {
        Environment environment = mock(Environment.class);
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        DeviceImageRepository imageRepository = mock(DeviceImageRepository.class);
        Device device = new Device();
        device.setDeviceCode("DRONE-01");
        when(environment.getProperty("DRONE_IMAGE_STORAGE", "local")).thenReturn("local");
        when(deviceRepository.findByDeviceCode("DRONE-01")).thenReturn(Optional.of(device));
        when(imageRepository.save(org.mockito.ArgumentMatchers.any(DeviceImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DeviceImageService service = new DeviceImageService(
                mock(S3ObjectStorageService.class),
                new AwsS3Properties(),
                environment,
                deviceRepository,
                imageRepository);
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "clip.mp4", "video/mp4", "fake-mp4".getBytes());

        DeviceImage saved = service.upload("MISSION_001", "DRONE-01", null, file, "VIDEO");

        org.assertj.core.api.Assertions.assertThat(saved.getType()).isEqualTo("VIDEO");
        org.assertj.core.api.Assertions.assertThat(saved.getContentType()).isEqualTo("video/mp4");
        org.assertj.core.api.Assertions.assertThat(saved.getS3Key()).contains("drone-videos");
    }
}
