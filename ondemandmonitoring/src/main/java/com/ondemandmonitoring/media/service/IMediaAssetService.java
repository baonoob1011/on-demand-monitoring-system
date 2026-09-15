package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.domain.MediaAsset;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface IMediaAssetService {

    MediaAsset upload(String deviceCode, MultipartFile file);

    MediaAsset upload(String missionId, String droneId, Instant capturedAt, MultipartFile file);

    MediaAsset upload(String missionId, String droneId, Instant capturedAt, MultipartFile file, String requestedMediaType);

    MediaAsset getById(String mediaId);

    List<MediaAsset> listByMission(String missionId, String requestedMediaType);

    MediaAsset getByDeviceAndId(String deviceCode, String mediaId);

    List<MediaAsset> listByDevice(String deviceCode, String requestedMediaType);

    void deleteByDeviceAndId(String deviceCode, String mediaId);

    MediaContent openMedia(MediaAsset mediaAsset);

    String createPresignedGetUrl(MediaAsset mediaAsset);

    long presignedUrlExpiresSeconds();

    record MediaContent(InputStream inputStream, long contentLength, String contentType, String fileName) {}
}
