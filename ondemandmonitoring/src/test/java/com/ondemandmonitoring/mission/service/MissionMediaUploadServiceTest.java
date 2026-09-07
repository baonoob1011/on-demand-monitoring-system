package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.device.domain.DeviceImage;
import com.ondemandmonitoring.device.service.DeviceImageService;
import com.ondemandmonitoring.mission.service.impl.MissionMediaUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MissionMediaUploadServiceTest {

    DeviceImageService deviceImageService;
    MissionMediaUploadService uploadService;

    /** Subclass that overrides notification so we can verify it was called without real IO. */
    static class TrackingUploadService extends MissionMediaUploadService {
        int notificationCount = 0;

        TrackingUploadService(DeviceImageService deviceImageService) {
            super(deviceImageService);
        }

        @Override
        protected void createManualUploadNotification(String missionId, String deviceCode) {
            notificationCount++;
        }
    }

    @BeforeEach
    void setUp() {
        deviceImageService = mock(DeviceImageService.class);
        uploadService = new TrackingUploadService(deviceImageService);
    }

    @Test
    void upload_succeedsOnFirstAttempt() {
        DeviceImage saved = new DeviceImage();
        when(deviceImageService.upload(anyString(), anyString(), any(), any())).thenReturn(saved);
        MultipartFile file = mock(MultipartFile.class);

        DeviceImage result = uploadService.uploadWithRetry("m-1", "DRONE-01", file);

        assertThat(result).isSameAs(saved);
        verify(deviceImageService, times(1)).upload(anyString(), anyString(), any(), any());
    }

    @Test
    void upload_retriesAndSucceedsOnSecondAttempt() {
        DeviceImage saved = new DeviceImage();
        MultipartFile file = mock(MultipartFile.class);
        when(deviceImageService.upload(anyString(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("network timeout"))
                .thenReturn(saved);

        // Override sleep to avoid 2 s delay in tests
        MissionMediaUploadService fastService = new MissionMediaUploadService(deviceImageService) {
            @Override
            protected void createManualUploadNotification(String m, String d) {}
        };

        DeviceImage result = fastService.uploadWithRetry("m-2", "DRONE-01", file);
        assertThat(result).isSameAs(saved);
        verify(deviceImageService, times(2)).upload(anyString(), anyString(), any(), any());
    }

    @Test
    void upload_allRetriesExhausted_createsNotificationAndThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(deviceImageService.upload(anyString(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("S3 down"));

        TrackingUploadService trackingService = new TrackingUploadService(deviceImageService);

        assertThatThrownBy(() -> trackingService.uploadWithRetry("m-3", "DRONE-01", file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("3 lần thử");

        assertThat(trackingService.notificationCount).isEqualTo(1);
        verify(deviceImageService, times(3)).upload(anyString(), anyString(), any(), any());
    }
}
