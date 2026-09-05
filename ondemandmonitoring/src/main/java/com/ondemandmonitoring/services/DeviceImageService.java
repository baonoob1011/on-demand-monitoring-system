package com.ondemandmonitoring.services;

import com.ondemandmonitoring.configuration.properties.AwsS3Properties;
import com.ondemandmonitoring.entities.DeviceImage;
import com.ondemandmonitoring.exception.ApiException;
import com.ondemandmonitoring.exception.ErrorCode;
import com.ondemandmonitoring.repositories.DeviceImageRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg");
    private static final String MEDIA_TYPE_IMAGE = "IMAGE";
    private static final String STORAGE_PROVIDER_S3 = "S3";
    private static final long PRESIGNED_URL_EXPIRES_SECONDS = 900;

    S3Client s3Client;
    S3Presigner s3Presigner;
    AwsS3Properties awsS3Properties;
    DeviceImageRepository deviceImageRepository;

    @Transactional
    public DeviceImage upload(String deviceCode, MultipartFile file) {
        return upload("UNASSIGNED", deviceCode, Instant.now(), file);
    }

    @Transactional
    public DeviceImage upload(String missionId, String droneId, Instant capturedAt, MultipartFile file) {
        validate(file);
        validateRequired("missionId", missionId);
        validateRequired("droneId", droneId);

        String bucket = awsS3Properties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AWS S3 bucket is not configured");
        }

        String originalFileName = safeFileName(file.getOriginalFilename());
        String contentType = file.getContentType();
        String key = buildS3Key(missionId, droneId);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot read uploaded image");
        } catch (RuntimeException exception) {
            log.error("Cannot upload image to S3. bucket={}, key={}", bucket, key, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot upload image to S3: " + exception.getMessage());
        }

        try {
            DeviceImage image = new DeviceImage();
            image.setDeviceCode(droneId);
            image.setMissionId(missionId);
            image.setType(MEDIA_TYPE_IMAGE);
            image.setStorageProvider(STORAGE_PROVIDER_S3);
            image.setOriginalFileName(originalFileName);
            image.setContentType(contentType);
            image.setFileSize(file.getSize());
            image.setS3Bucket(bucket);
            image.setS3Key(key);
            image.setS3Url("s3://" + bucket + "/" + key);
            image.setCapturedAt(capturedAt == null ? Instant.now() : capturedAt);

            return deviceImageRepository.save(image);
        } catch (RuntimeException exception) {
            cleanupUploadedObject(bucket, key);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public DeviceImage getById(String mediaId) {
        return deviceImageRepository.findById(mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Media not found"));
    }

    public String createPresignedGetUrl(DeviceImage image) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(image.getS3Bucket())
                .key(image.getS3Key())
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGNED_URL_EXPIRES_SECONDS))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public long presignedUrlExpiresSeconds() {
        return PRESIGNED_URL_EXPIRES_SECONDS;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Image file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PNG and JPEG images are allowed");
        }
    }

    private String buildS3Key(String missionId, String droneId) {
        String prefix = awsS3Properties.getPrefix();
        String normalizedPrefix = prefix == null ? "" : prefix.strip().replaceAll("^/+|/+$", "");
        String timestamp = Instant.now().toString().replaceAll("[^0-9A-Za-z]", "");
        String fileName = timestamp + "-" + UUID.randomUUID() + ".jpg";
        String missionPath = safePathSegment(missionId);
        String dronePath = safePathSegment(droneId);
        String key = "missions/" + missionPath + "/drones/" + dronePath + "/images/" + fileName;

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
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException cleanupException) {
            log.warn("Failed to cleanup S3 object after DB error. bucket={}, key={}", bucket, key, cleanupException);
        }
    }
}
