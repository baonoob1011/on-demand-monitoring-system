package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import com.ondemandmonitoring.device.service.IDeviceTelemetryService;
import com.ondemandmonitoring.mapillary.MapillaryImageCandidate;
import com.ondemandmonitoring.mapillary.MapillaryImageService;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.service.IMediaAssetService;
import com.ondemandmonitoring.media.service.IMediaAssetService.ReferenceProvenance;
import com.ondemandmonitoring.mission.dto.response.MissionMediaContext;
import com.ondemandmonitoring.mission.dto.response.ReferenceCaptureResponse;
import com.ondemandmonitoring.mission.repository.MissionDeviceAssignmentRepository;
import com.ondemandmonitoring.mission.service.IMissionMediaAccessService;
import com.ondemandmonitoring.mission.service.IMissionReferenceCaptureService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MissionReferenceCaptureServiceImpl implements IMissionReferenceCaptureService {

    public static final String SOURCE_TYPE = "MAPILLARY_REFERENCE";
    /** Reference capture is also useful while the drone is connected and ready, before take-off. */
    static final java.util.Set<com.ondemandmonitoring.mission.enums.MissionStatus> PRE_FLIGHT_CAPTURE_STATUSES =
            java.util.Set.of(
                    com.ondemandmonitoring.mission.enums.MissionStatus.CONNECTED,
                    com.ondemandmonitoring.mission.enums.MissionStatus.PREFLIGHT_CHECKING,
                    com.ondemandmonitoring.mission.enums.MissionStatus.READY_TO_FLY);
    static final Duration DEDUPE_WINDOW = Duration.ofSeconds(3);

    private final IMissionMediaAccessService missionAccess;
    private final MissionDeviceAssignmentRepository deviceAssignments;
    private final IDeviceTelemetryService telemetryService;
    private final MapillaryImageService mapillary;
    private final IMediaAssetService mediaAssetService;
    private final long telemetryMaxAgeSeconds;
    private final ConcurrentMap<String, Object> missionLocks = new ConcurrentHashMap<>();

    public MissionReferenceCaptureServiceImpl(
            IMissionMediaAccessService missionAccess,
            MissionDeviceAssignmentRepository deviceAssignments,
            IDeviceTelemetryService telemetryService,
            MapillaryImageService mapillary,
            IMediaAssetService mediaAssetService,
            @Value("${simulation.telemetry.max-age-seconds:5}") long telemetryMaxAgeSeconds) {
        this.missionAccess = missionAccess;
        this.deviceAssignments = deviceAssignments;
        this.telemetryService = telemetryService;
        this.mapillary = mapillary;
        this.mediaAssetService = mediaAssetService;
        this.telemetryMaxAgeSeconds = telemetryMaxAgeSeconds;
    }

    @Override
    public ReferenceCaptureResponse captureMapillaryReference(String missionIdentifier) {
        MissionMediaContext context = missionAccess.authorizeOperator(missionIdentifier);
        if (!context.isCaptureAllowed() && (context.getStatus() == null || !PRE_FLIGHT_CAPTURE_STATUSES.contains(context.getStatus()))) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission chưa ở trạng thái cho phép chụp ảnh.");
        }
        String missionId = context.getId();
        // Serialize per mission so a double click cannot create two assets.
        synchronized (missionLocks.computeIfAbsent(missionId, key -> new Object())) {
            return capture(missionId);
        }
    }

    private ReferenceCaptureResponse capture(String missionId) {
        List<String> deviceIds = deviceAssignments.findCurrentDeviceIds(missionId);
        if (deviceIds.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mission chưa có drone được gán.");
        }
        String deviceId = deviceIds.get(0);

        Optional<MediaAsset> recent = mediaAssetService.findRecentBySource(
                missionId, SOURCE_TYPE, Instant.now().minus(DEDUPE_WINDOW));
        if (recent.isPresent()) {
            return toResponse(recent.get(), true);
        }

        DeviceTelemetry telemetry = telemetryService.findLatest(missionId, deviceId)
                .orElseThrow(() -> {
                    log.warn("Reference capture: no telemetry row for missionId={} deviceId={}", missionId, deviceId);
                    return new ApiException(ErrorCode.DRONE_TELEMETRY_UNAVAILABLE);
                });
        Double lat = telemetry.getLatitude();
        Double lon = telemetry.getLongitude();
        if (lat == null || lon == null || !Double.isFinite(lat) || !Double.isFinite(lon)) {
            log.warn("Reference capture: latest telemetry has no GPS. missionId={} deviceId={} recordedAt={} simX={} simY={}",
                    missionId, deviceId, telemetry.getRecordedAt(), telemetry.getSimX(), telemetry.getSimY());
            throw new ApiException(ErrorCode.DRONE_TELEMETRY_UNAVAILABLE);
        }
        Instant recordedAt = telemetry.getRecordedAt();
        if (recordedAt == null
                || Duration.between(recordedAt, Instant.now()).compareTo(Duration.ofSeconds(telemetryMaxAgeSeconds)) > 0) {
            throw new ApiException(ErrorCode.DRONE_TELEMETRY_STALE);
        }

        MapillaryImageCandidate image = mapillary.findNearestImage(lat, lon)
                .orElseThrow(() -> new ApiException(ErrorCode.MAPILLARY_IMAGE_NOT_FOUND));
        byte[] bytes = mapillary.download(image);
        double distance = MapillaryImageService.distanceMeters(lat, lon, image.latitude(), image.longitude());
        Double altitude = telemetry.getAbsoluteAltitudeM() != null
                ? telemetry.getAbsoluteAltitudeM()
                : telemetry.getRelativeAltitudeM();

        MediaAsset stored = mediaAssetService.uploadReference(
                missionId,
                deviceId,
                Instant.now(),
                new ByteArrayMultipartFile("mapillary-" + image.imageId() + ".jpg", "image/jpeg", bytes),
                new ReferenceProvenance(
                        SOURCE_TYPE,
                        image.imageId(),
                        image.capturedAt(),
                        image.latitude(),
                        image.longitude(),
                        lat,
                        lon,
                        altitude,
                        distance));
        log.info("Mapillary reference stored. missionId={} mediaId={} distanceM={}",
                missionId, stored.getId(), Math.round(distance));
        return toResponse(stored, false);
    }

    private ReferenceCaptureResponse toResponse(MediaAsset asset, boolean duplicate) {
        String url = null;
        try {
            url = mediaAssetService.createPresignedGetUrl(asset);
        } catch (RuntimeException exception) {
            log.debug("Cannot create media url for {}", asset.getId());
        }
        return ReferenceCaptureResponse.builder()
                .mediaAssetId(asset.getId())
                .missionId(asset.getMissionId())
                .sourceType(asset.getSourceType())
                .dronePosition(ReferenceCaptureResponse.Position.builder()
                        .lat(asset.getCaptureLatitude())
                        .lon(asset.getCaptureLongitude())
                        .altitude(asset.getCaptureAltitudeM())
                        .build())
                .sourcePosition(ReferenceCaptureResponse.Position.builder()
                        .lat(asset.getSourceLatitude())
                        .lon(asset.getSourceLongitude())
                        .build())
                .distanceMeters(asset.getSourceDistanceMeters())
                .mapillaryImageId(asset.getSourceReferenceId())
                .capturedAt(asset.getCapturedAt())
                .mediaUrl(url)
                .duplicate(duplicate)
                .build();
    }

    /** In-memory MultipartFile so downloaded bytes reuse the existing upload pipeline unchanged. */
    static final class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final String contentType;
        private final byte[] bytes;

        ByteArrayMultipartFile(String name, String contentType, byte[] bytes) {
            this.name = name;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public Resource getResource() { return new ByteArrayResource(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
