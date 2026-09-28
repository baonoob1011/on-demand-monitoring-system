package com.ondemandmonitoring.devicecheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheck;
import com.ondemandmonitoring.devicecheck.dto.request.MediaProbeRequest;
import com.ondemandmonitoring.devicecheck.dto.request.PreDeviceCheckItemUpdateRequest;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceItemStatus;
import com.ondemandmonitoring.devicecheck.repository.PersistedPreDeviceCheckRepository;
import com.ondemandmonitoring.devicecheck.service.impl.MediaProbeServiceImpl;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MediaProbeServiceImplTest {

    @Mock PersistedPreDeviceCheckRepository runs;
    @Mock IPersistedPreDeviceCheckService preDeviceChecks;
    @Mock IMissionService missions;
    @Mock S3ObjectStorageService storage;

    MediaProbeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MediaProbeServiceImpl(runs, preDeviceChecks, missions, storage);
        ReflectionTestUtils.setField(service, "maxBytes", 262144L);
        ReflectionTestUtils.setField(service, "probePrefix", "diagnostics/media-probes");
    }

    @Test
    void verifiesChecksumStorageRoundTripAndCleanup() throws Exception {
        byte[] jpeg = jpeg();
        stubContext();
        when(storage.bucket()).thenReturn("probe-bucket");
        when(storage.open(eq("probe-bucket"), any())).thenReturn(
                new S3ObjectStorageService.StoredObjectStream(
                        new ByteArrayInputStream(jpeg), (long) jpeg.length, "image/jpeg"));

        var response = service.verify("run-1", request(jpeg, sha256(jpeg)));

        assertThat(response.getStatus()).isEqualTo("PASSED");
        assertThat(response.getDeviceId()).isEqualTo("device-1");
        assertThat(response.isChecksumVerified()).isTrue();
        assertThat(response.isCleanupVerified()).isTrue();
        verify(storage).put(any(), eq("image/jpeg"), eq((long) jpeg.length), any(), eq("[MEDIA-PROBE]"));
        verify(storage).delete(eq("probe-bucket"), any());

        ArgumentCaptor<PreDeviceCheckItemUpdateRequest> update =
                ArgumentCaptor.forClass(PreDeviceCheckItemUpdateRequest.class);
        verify(preDeviceChecks).update(eq("run-1"), eq("MEDIA"), update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo(PreDeviceItemStatus.PASSED);
    }

    @Test
    void rejectsChecksumMismatchAndPersistsFailedResult() throws Exception {
        byte[] jpeg = jpeg();
        stubContext();

        assertThatThrownBy(() -> service.verify("run-1", request(jpeg, "0".repeat(64))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("checksum");

        ArgumentCaptor<PreDeviceCheckItemUpdateRequest> update =
                ArgumentCaptor.forClass(PreDeviceCheckItemUpdateRequest.class);
        verify(preDeviceChecks).update(eq("run-1"), eq("MEDIA"), update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo(PreDeviceItemStatus.FAILED);
    }

    private void stubContext() {
        Mission mission = new Mission();
        mission.setId("mission-1");
        PersistedPreDeviceCheck run = new PersistedPreDeviceCheck();
        run.setId("run-1");
        run.setMission(mission);
        when(runs.findById("run-1")).thenReturn(Optional.of(run));
        when(missions.getByIdResponse("mission-1")).thenReturn(
                MissionResponse.builder().id("mission-1").deviceId("device-1").build());
    }

    private MediaProbeRequest request(byte[] jpeg, String checksum) {
        MediaProbeRequest request = new MediaProbeRequest();
        request.setChecksumSha256(checksum);
        request.setFile(new MockMultipartFile("file", "probe.jpg", "image/jpeg", jpeg));
        return request;
    }

    private byte[] jpeg() {
        return new byte[] {(byte) 0xff, (byte) 0xd8, 1, 2, 3, (byte) 0xff, (byte) 0xd9};
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
