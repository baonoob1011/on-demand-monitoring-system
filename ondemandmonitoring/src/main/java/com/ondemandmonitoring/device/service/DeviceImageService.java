package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceImage;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.enums.DeviceType;
import com.ondemandmonitoring.s3.AwsS3Properties;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.s3.S3ObjectStorageService.StoredObject;
import com.ondemandmonitoring.s3.S3ObjectStorageService.StoredObjectStream;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.DeviceImageRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "video/mp4");
    private static final String MEDIA_TYPE_IMAGE = "IMAGE";
    private static final String MEDIA_TYPE_VIDEO = "VIDEO";
    private static final String STORAGE_PROVIDER_S3 = "S3";
    private static final String STORAGE_PROVIDER_LOCAL = "LOCAL";
    private static final Path LOCAL_IMAGE_DIR = Path.of("uploads", "drone-images");

    S3ObjectStorageService s3ObjectStorageService;
    AwsS3Properties awsS3Properties;
    Environment environment;
    DeviceRepository deviceRepository;
    DeviceImageRepository deviceImageRepository;

    @Transactional
    public DeviceImage upload(String deviceCode, MultipartFile file) {
        return upload("UNASSIGNED", deviceCode, Instant.now(), file, MEDIA_TYPE_IMAGE);
    }

    @Transactional
    public DeviceImage upload(String missionId, String droneId, Instant capturedAt, MultipartFile file) {
        return upload(missionId, droneId, capturedAt, file, MEDIA_TYPE_IMAGE);
    }

    @Transactional
    public DeviceImage upload(String missionId, String droneId, Instant capturedAt, MultipartFile file, String requestedMediaType) {
        String mediaType = validate(file, requestedMediaType);
        validateRequired("missionId", missionId);
        validateRequired("droneId", droneId);
        Device device = getOrCreateDrone(droneId);

        String originalFileName = safeFileName(file.getOriginalFilename());
        String contentType = file.getContentType();

        if (!useS3Storage()) {
            return saveLocal(device, missionId, droneId, capturedAt, file, originalFileName, contentType, mediaType);
        }

        String bucket = s3ObjectStorageService.bucket();
        if (bucket == null || bucket.isBlank()) {
            log.warn("AWS S3 bucket is not configured; storing image locally");
            return saveLocal(device, missionId, droneId, capturedAt, file, originalFileName, contentType, mediaType);
        }

        String key = buildS3Key(missionId, droneId, mediaType);
        String diagnosticPrefix = MEDIA_TYPE_VIDEO.equals(mediaType) ? "[S3-VIDEO]" : "[S3-IMAGE]";
        StoredObject storedObject;

        try {
            storedObject = s3ObjectStorageService.put(
                    key,
                    contentType,
                    file.getSize(),
                    file.getInputStream(),
                    diagnosticPrefix);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot read uploaded image");
        } catch (RuntimeException exception) {
            log.error("Cannot upload image to S3. bucket={}, key={}", bucket, key, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot upload media to S3: " + rootMessage(exception));
        }

        try {
            DeviceImage image = new DeviceImage();
            image.setDeviceCode(droneId);
            image.setDevice(device);
            image.setMissionId(missionId);
            image.setType(mediaType);
            image.setStorageProvider(STORAGE_PROVIDER_S3);
            image.setOriginalFileName(originalFileName);
            image.setContentType(contentType);
            image.setFileSize(file.getSize());
            image.setS3Bucket(storedObject.bucket());
            image.setS3Key(storedObject.key());
            image.setS3Url(storedObject.url());
            image.setCapturedAt(capturedAt == null ? Instant.now() : capturedAt);

            return deviceImageRepository.save(image);
        } catch (RuntimeException exception) {
            cleanupUploadedObject(storedObject.bucket(), storedObject.key());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public DeviceImage getById(String mediaId) {
        return deviceImageRepository.findById(mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Media not found"));
    }

    @Transactional(readOnly = true)
    public List<DeviceImage> listByMission(String missionId, String requestedMediaType) {
        validateRequired("missionId", missionId);
        if (requestedMediaType == null || requestedMediaType.isBlank()) {
            return deviceImageRepository.findByMissionIdOrderByCapturedAtDesc(missionId);
        }

        String mediaType = normalizeMediaType(requestedMediaType);
        return deviceImageRepository.findByMissionIdAndTypeOrderByCapturedAtDesc(missionId, mediaType);
    }

    public MediaContent openMedia(DeviceImage image) {
        if (STORAGE_PROVIDER_LOCAL.equalsIgnoreCase(image.getStorageProvider())) {
            Path path = Path.of(image.getS3Url());
            try {
                return new MediaContent(
                        Files.newInputStream(path),
                        Files.size(path),
                        image.getContentType(),
                        image.getOriginalFileName());
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot read local media file");
            }
        }

        try {
            StoredObjectStream stream = s3ObjectStorageService.open(image.getS3Bucket(), image.getS3Key());
            return new MediaContent(
                    stream.inputStream(),
                    stream.contentLength() == null ? image.getFileSize() : stream.contentLength(),
                    stream.contentType() == null || stream.contentType().isBlank()
                            ? image.getContentType()
                            : stream.contentType(),
                    image.getOriginalFileName());
        } catch (RuntimeException exception) {
            log.error("Cannot read media from S3. bucket={}, key={}", image.getS3Bucket(), image.getS3Key(), exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot read media from S3: " + rootMessage(exception));
        }
    }

    public String createPresignedGetUrl(DeviceImage image) {
        if (STORAGE_PROVIDER_LOCAL.equalsIgnoreCase(image.getStorageProvider())) {
            return image.getS3Url();
        }

        return s3ObjectStorageService.createPresignedGetUrl(image.getS3Bucket(), image.getS3Key());
    }

    public long presignedUrlExpiresSeconds() {
        return s3ObjectStorageService.presignedUrlExpiresSeconds();
    }

    private String validate(MultipartFile file, String requestedMediaType) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Media file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PNG, JPEG, and MP4 media are allowed");
        }

        String mediaType = contentType.equals("video/mp4") ? MEDIA_TYPE_VIDEO : MEDIA_TYPE_IMAGE;
        if (requestedMediaType != null && !requestedMediaType.isBlank()) {
            String normalized = requestedMediaType.strip().toUpperCase();
            if (!normalized.equals(mediaType)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "mediaType does not match uploaded content type");
            }
        }

        return mediaType;
    }

    private String buildS3Key(String missionId, String droneId, String mediaType) {
        String prefix = awsS3Properties.getPrefix();
        String normalizedPrefix = prefix == null ? "" : prefix.strip().replaceAll("^/+|/+$", "");
        String timestamp = Instant.now().toString().replaceAll("[^0-9A-Za-z]", "");
        boolean video = MEDIA_TYPE_VIDEO.equals(mediaType);
        String fileName = timestamp + "-" + UUID.randomUUID() + (video ? ".mp4" : ".jpg");
        String missionPath = safePathSegment(missionId);
        String dronePath = safePathSegment(droneId);
        String folder = "images";
        String key = "missions/" + missionPath + "/drones/" + dronePath + "/" + folder + "/" + fileName;

        if (normalizedPrefix.isBlank()) {
            return key;
        }

        return normalizedPrefix + "/" + key;
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "gazebo-screenshot.png";
        }

        return originalFileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String safePathSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void validateRequired(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, field + " is required");
        }
    }

    private void cleanupUploadedObject(String bucket, String key) {
        s3ObjectStorageService.deleteQuietly(bucket, key);
    }

    private DeviceImage saveLocal(
            Device device,
            String missionId,
            String droneId,
            Instant capturedAt,
            MultipartFile file,
            String originalFileName,
            String contentType,
            String mediaType) {
        String timestamp = Instant.now().toString().replaceAll("[^0-9A-Za-z]", "");
        boolean video = MEDIA_TYPE_VIDEO.equals(mediaType);
        String fileName = timestamp + "-" + UUID.randomUUID() + (video ? ".mp4" : ".jpg");
        String folder = video ? "drone-videos" : "drone-images";
        Path relativePath = LOCAL_IMAGE_DIR
                .getParent()
                .resolve(folder)
                .resolve(safePathSegment(missionId))
                .resolve(safePathSegment(droneId))
                .resolve(fileName);

        try {
            Files.createDirectories(relativePath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, relativePath);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot store media locally after S3 upload failed");
        }

        DeviceImage image = new DeviceImage();
        image.setDeviceCode(droneId);
        image.setDevice(device);
        image.setMissionId(missionId);
        image.setType(mediaType);
        image.setStorageProvider(STORAGE_PROVIDER_LOCAL);
        image.setOriginalFileName(originalFileName);
        image.setContentType(contentType);
        image.setFileSize(file.getSize());
        image.setS3Bucket(STORAGE_PROVIDER_LOCAL);
        image.setS3Key(relativePath.toString().replace('\\', '/'));
        image.setS3Url(relativePath.toAbsolutePath().toString());
        image.setCapturedAt(capturedAt == null ? Instant.now() : capturedAt);

        return deviceImageRepository.save(image);
    }

    private boolean useS3Storage() {
        String storage = environment.getProperty("DRONE_IMAGE_STORAGE", "local");
        return STORAGE_PROVIDER_S3.equalsIgnoreCase(storage);
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }

        return message;
    }

    private Device getOrCreateDrone(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode)
                .orElseGet(() -> {
                    Device device = new Device();
                    device.setDeviceCode(deviceCode);
                    device.setDeviceName("PX4 SITL Drone");
                    device.setDeviceType(DeviceType.DRONE);
                    device.setStatus(DeviceStatus.AVAILABLE);
                    device.setLastSeenAt(LocalDateTime.now());
                    return deviceRepository.save(device);
                });
    }

    private String normalizeMediaType(String requestedMediaType) {
        String mediaType = requestedMediaType.strip().toUpperCase();
        if (!MEDIA_TYPE_IMAGE.equals(mediaType) && !MEDIA_TYPE_VIDEO.equals(mediaType)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "mediaType must be IMAGE or VIDEO");
        }
        return mediaType;
    }

    public record MediaContent(InputStream inputStream, long contentLength, String contentType, String fileName) {}
}
