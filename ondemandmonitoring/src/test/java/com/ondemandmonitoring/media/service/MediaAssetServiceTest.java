package com.ondemandmonitoring.media.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.s3.AwsS3Properties;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.service.impl.MediaAssetService;
import com.ondemandmonitoring.common.exception.ApiException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.multipart.MultipartFile;

class MediaAssetServiceTest {

    @Test
    void upload_rejectsMissingFile() {
        MediaAssetService service = new MediaAssetService(
                mock(S3ObjectStorageService.class),
                new AwsS3Properties(),
                mock(Environment.class),
                mock(DeviceRepository.class),
                mock(MediaAssetRepository.class));

        assertThatThrownBy(() -> service.upload("DRONE-01", null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Media file is required");
    }

    @Test
    void upload_rejectsUnsupportedContentType() {
        MediaAssetService service = new MediaAssetService(
                mock(S3ObjectStorageService.class),
                new AwsS3Properties(),
                mock(Environment.class),
                mock(DeviceRepository.class),
                mock(MediaAssetRepository.class));
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "capture.txt", "text/plain", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.upload("DRONE-01", file))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only PNG, JPEG, and MP4 media are allowed");
    }

    @Test
    void upload_rejectsMismatchedVideoMediaType() {
        MediaAssetService service = new MediaAssetService(
                mock(S3ObjectStorageService.class),
                new AwsS3Properties(),
                mock(Environment.class),
                mock(DeviceRepository.class),
                mock(MediaAssetRepository.class));
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
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        Device device = new Device();
        device.setDeviceCode("DRONE-01");
        when(environment.getProperty("DRONE_IMAGE_STORAGE", "local")).thenReturn("local");
        when(deviceRepository.findByDeviceCode("DRONE-01")).thenReturn(Optional.of(device));
        when(mediaAssetRepository.save(org.mockito.ArgumentMatchers.any(MediaAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MediaAssetService service = new MediaAssetService(
                mock(S3ObjectStorageService.class),
                new AwsS3Properties(),
                environment,
                deviceRepository,
                mediaAssetRepository);
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "clip.mp4", "video/mp4", "fake-mp4".getBytes());

        MediaAsset saved = service.upload("MISSION_001", "DRONE-01", null, file, "VIDEO");

        org.assertj.core.api.Assertions.assertThat(saved.getType()).isEqualTo("VIDEO");
        org.assertj.core.api.Assertions.assertThat(saved.getContentType()).isEqualTo("video/mp4");
        org.assertj.core.api.Assertions.assertThat(saved.getS3Key()).contains("drone-videos");
    }

    @Test
    void upload_storesImagesAndVideosInSeparateS3Folders() {
        Environment environment = mock(Environment.class);
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
        S3ObjectStorageService s3ObjectStorageService = mock(S3ObjectStorageService.class);
        AwsS3Properties properties = new AwsS3Properties();
        properties.setBucket("bucket");
        Device device = new Device();
        device.setDeviceCode("DRONE-01");
        when(environment.getProperty("DRONE_IMAGE_STORAGE", "local")).thenReturn("s3");
        when(deviceRepository.findByDeviceCode("DRONE-01")).thenReturn(Optional.of(device));
        when(s3ObjectStorageService.bucket()).thenReturn("bucket");
        when(s3ObjectStorageService.put(any(), any(), eq(8L), any(InputStream.class), any()))
                .thenAnswer(invocation -> new S3ObjectStorageService.StoredObject(
                        "bucket",
                        invocation.getArgument(0),
                        "s3://bucket/" + invocation.getArgument(0)));
        when(mediaAssetRepository.save(org.mockito.ArgumentMatchers.any(MediaAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MediaAssetService service = new MediaAssetService(
                s3ObjectStorageService,
                properties,
                environment,
                deviceRepository,
                mediaAssetRepository);
        MultipartFile imageFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "capture.jpg", "image/jpeg", "fake-jpg".getBytes());
        MultipartFile videoFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "clip.mp4", "video/mp4", "fake-mp4".getBytes());

        MediaAsset savedImage = service.upload("MISSION_001", "DRONE-01", null, imageFile, "IMAGE");
        MediaAsset savedVideo = service.upload("MISSION_001", "DRONE-01", null, videoFile, "VIDEO");

        org.assertj.core.api.Assertions.assertThat(savedImage.getS3Key())
                .contains("/images/")
                .doesNotContain("/videos/");
        org.assertj.core.api.Assertions.assertThat(savedVideo.getS3Key())
                .contains("/videos/")
                .doesNotContain("/images/");
    }
}
