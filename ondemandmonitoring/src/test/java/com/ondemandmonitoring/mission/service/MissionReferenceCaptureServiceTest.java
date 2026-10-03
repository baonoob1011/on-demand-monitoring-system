package com.ondemandmonitoring.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import com.ondemandmonitoring.device.service.IDeviceTelemetryService;
import com.ondemandmonitoring.mapillary.MapillaryClient;
import com.ondemandmonitoring.mapillary.MapillaryImageCandidate;
import com.ondemandmonitoring.mapillary.MapillaryImageService;
import com.ondemandmonitoring.mapillary.MapillaryProperties;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.service.IMediaAssetService;
import com.ondemandmonitoring.mission.dto.response.MissionMediaContext;
import com.ondemandmonitoring.mission.dto.response.ReferenceCaptureResponse;
import com.ondemandmonitoring.mission.repository.MissionDeviceAssignmentRepository;
import com.ondemandmonitoring.mission.service.impl.MissionReferenceCaptureServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MissionReferenceCaptureServiceTest {
    private final IMissionMediaAccessService access = mock(IMissionMediaAccessService.class);
    private final MissionDeviceAssignmentRepository assignments = mock(MissionDeviceAssignmentRepository.class);
    private final IDeviceTelemetryService telemetry = mock(IDeviceTelemetryService.class);
    private final MapillaryClient client = mock(MapillaryClient.class);
    private final IMediaAssetService media = mock(IMediaAssetService.class);
    private MissionReferenceCaptureServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MissionReferenceCaptureServiceImpl(access, assignments, telemetry,
                new MapillaryImageService(client, new MapillaryProperties()), media, 5);
        when(access.authorizeOperator("m1")).thenReturn(new MissionMediaContext("m1", true));
        when(assignments.findCurrentDeviceIds("m1")).thenReturn(List.of("d1"));
        when(media.findRecentBySource(eq("m1"), eq("MAPILLARY_REFERENCE"), any())).thenReturn(Optional.empty());
    }

    private DeviceTelemetry fix(Double lat, Double lon, Instant at) {
        DeviceTelemetry t = new DeviceTelemetry();
        t.setLatitude(lat);
        t.setLongitude(lon);
        t.setAbsoluteAltitudeM(42.0);
        t.setRecordedAt(at);
        return t;
    }

    @Test
    void rejectsWhenNoGps() {
        when(telemetry.findLatest("m1", "d1")).thenReturn(Optional.of(fix(null, null, Instant.now())));
        assertThatThrownBy(() -> service.captureMapillaryReference("m1"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.DRONE_TELEMETRY_UNAVAILABLE);

    }

    @Test
    void rejectsStaleTelemetry() {
        when(telemetry.findLatest("m1", "d1"))
                .thenReturn(Optional.of(fix(10.77, 106.70, Instant.now().minusSeconds(30))));
        assertThatThrownBy(() -> service.captureMapillaryReference("m1"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.DRONE_TELEMETRY_STALE);
    }

    @Test
    void noImageCreatesNoAsset() {
        when(telemetry.findLatest("m1", "d1")).thenReturn(Optional.of(fix(10.77, 106.70, Instant.now())));
        when(client.searchNearby(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.captureMapillaryReference("m1"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.MAPILLARY_IMAGE_NOT_FOUND);
        verify(media, never()).uploadReference(any(), any(), any(), any(), any());
    }

    @Test
    void mapillaryErrorPropagatesWithoutAsset() {
        when(telemetry.findLatest("m1", "d1")).thenReturn(Optional.of(fix(10.77, 106.70, Instant.now())));
        when(client.searchNearby(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new ApiException(ErrorCode.MAPILLARY_UNAVAILABLE));
        assertThatThrownBy(() -> service.captureMapillaryReference("m1"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.MAPILLARY_UNAVAILABLE);
        verify(media, never()).uploadReference(any(), any(), any(), any(), any());
    }

    @Test
    void storesImageThroughExistingUploadPipelineWithProvenance() {
        when(telemetry.findLatest("m1", "d1")).thenReturn(Optional.of(fix(10.7700, 106.7000, Instant.now())));
        var candidate = new MapillaryImageCandidate("img-9", 10.7701, 106.7001, Instant.now(), null, false, "https://cdn/x.jpg");
        when(client.searchNearby(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(candidate));
        when(client.download("https://cdn/x.jpg")).thenReturn(new byte[] {1, 2, 3});
        MediaAsset stored = new MediaAsset();
        stored.setId("media-1");
        stored.setSourceType("MAPILLARY_REFERENCE");
        stored.setSourceReferenceId("img-9");
        when(media.uploadReference(eq("m1"), eq("d1"), any(), any(), any())).thenReturn(stored);

        ReferenceCaptureResponse response = service.captureMapillaryReference("m1");

        ArgumentCaptor<IMediaAssetService.ReferenceProvenance> provenance =
                ArgumentCaptor.forClass(IMediaAssetService.ReferenceProvenance.class);
        verify(media).uploadReference(eq("m1"), eq("d1"), any(), any(), provenance.capture());
        assertThat(provenance.getValue().sourceType()).isEqualTo("MAPILLARY_REFERENCE");
        assertThat(provenance.getValue().captureLatitude()).isEqualTo(10.7700);
        assertThat(provenance.getValue().sourceLatitude()).isEqualTo(10.7701);
        assertThat(provenance.getValue().sourceDistanceMeters()).isGreaterThan(0);
        assertThat(response.getMediaAssetId()).isEqualTo("media-1");
        assertThat(response.getSourceType()).isEqualTo("MAPILLARY_REFERENCE");
    }

    @Test
    void duplicateClickReturnsExistingAsset() {
        MediaAsset existing = new MediaAsset();
        existing.setId("media-0");
        existing.setSourceType("MAPILLARY_REFERENCE");
        when(media.findRecentBySource(eq("m1"), eq("MAPILLARY_REFERENCE"), any())).thenReturn(Optional.of(existing));
        ReferenceCaptureResponse response = service.captureMapillaryReference("m1");
        assertThat(response.isDuplicate()).isTrue();
        verify(media, never()).uploadReference(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWhenMissionNotInCaptureState() {
        when(access.authorizeOperator("m1")).thenReturn(new MissionMediaContext("m1", false));
        assertThatThrownBy(() -> service.captureMapillaryReference("m1"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.MISSION_STATUS_INVALID);
    }
}
