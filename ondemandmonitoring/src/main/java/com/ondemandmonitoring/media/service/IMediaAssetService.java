package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.domain.MediaAsset;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

public interface IMediaAssetService {

    MediaAsset upload(String deviceId, MultipartFile file);

    MediaAsset upload(String missionId, String deviceId, Instant capturedAt, MultipartFile file);

    MediaAsset upload(String missionId, String deviceId, Instant capturedAt, MultipartFile file, String requestedMediaType);

    /**
     * Stores a reference image through the normal upload pipeline and records its provenance.
     * Reuses {@link #upload(String, String, Instant, MultipartFile, String)}.
     */
    MediaAsset uploadReference(String missionId, String deviceId, Instant capturedAt,
            MultipartFile file, ReferenceProvenance provenance);

    /** Most recent asset of the given source type created for the mission at or after {@code since}. */
    Optional<MediaAsset> findRecentBySource(String missionId, String sourceType, Instant since);

    MediaAsset getById(String mediaId);

    List<MediaAsset> listByMission(String missionId, String requestedMediaType);

    MediaAsset getByDeviceAndId(String deviceId, String mediaId);

    List<MediaAsset> listByDevice(String deviceId, String requestedMediaType);

    void deleteByDeviceAndId(String deviceId, String mediaId);

    MediaContent openMedia(MediaAsset mediaAsset);

    String createPresignedGetUrl(MediaAsset mediaAsset);

    long presignedUrlExpiresSeconds();

    record ReferenceProvenance(
            String sourceType,
            String sourceReferenceId,
            Instant sourceCapturedAt,
            Double sourceLatitude,
            Double sourceLongitude,
            Double captureLatitude,
            Double captureLongitude,
            Double captureAltitudeM,
            Double sourceDistanceMeters) {}

    record MediaContent(InputStream inputStream, long contentLength, String contentType, String fileName) {}
}
