package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.service.IMediaAssetService;
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

    IMediaAssetService mediaAssetService;
    MissionMediaUploadService uploadService;

    /** Subclass that overrides notification so we can verify it was called without real IO. */
    static class TrackingUploadService extends MissionMediaUploadService {
        int notificationCount = 0;

        TrackingUploadService(IMediaAssetService mediaAssetService) {
            super(mediaAssetService);
        }

        @Override
        protected void createManualUploadNotification(String missionId, String deviceCode) {
            notificationCount++;
        }
    }

    @BeforeEach
    void setUp() {
        mediaAssetService = mock(IMediaAssetService.class);
        uploadService = new TrackingUploadService(mediaAssetService);
    }

    @Test
    void upload_succeedsOnFirstAttempt() {
        MediaAsset saved = new MediaAsset();
        when(mediaAssetService.upload(anyString(), anyString(), any(), any(), any())).thenReturn(saved);
        MultipartFile file = mock(MultipartFile.class);

        MediaAsset result = uploadService.uploadWithRetry("m-1", "DRONE-01", file);

        assertThat(result).isSameAs(saved);
        verify(mediaAssetService, times(1)).upload(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void upload_retriesAndSucceedsOnSecondAttempt() {
        MediaAsset saved = new MediaAsset();
        MultipartFile file = mock(MultipartFile.class);
        when(mediaAssetService.upload(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("network timeout"))
                .thenReturn(saved);

        // Override sleep to avoid 2 s delay in tests
        MissionMediaUploadService fastService = new MissionMediaUploadService(mediaAssetService) {
            @Override
            protected void createManualUploadNotification(String m, String d) {}
        };

        MediaAsset result = fastService.uploadWithRetry("m-2", "DRONE-01", file);
        assertThat(result).isSameAs(saved);
        verify(mediaAssetService, times(2)).upload(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void upload_allRetriesExhausted_createsNotificationAndThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(mediaAssetService.upload(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("S3 down"));

        TrackingUploadService trackingService = new TrackingUploadService(mediaAssetService);

        assertThatThrownBy(() -> trackingService.uploadWithRetry("m-3", "DRONE-01", file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("3 lần thử");

        assertThat(trackingService.notificationCount).isEqualTo(1);
        verify(mediaAssetService, times(3)).upload(anyString(), anyString(), any(), any(), any());
    }
}
