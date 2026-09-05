package com.ondemandmonitoring.services;

import com.ondemandmonitoring.configuration.properties.AwsS3Properties;
import com.ondemandmonitoring.entities.DeviceImage;
import com.ondemandmonitoring.exception.ApiException;
import com.ondemandmonitoring.exception.ErrorCode;
import com.ondemandmonitoring.repositories.DeviceImageRepository;
import java.io.IOException;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg");

    S3Client s3Client;
    AwsS3Properties awsS3Properties;
    DeviceImageRepository deviceImageRepository;

    @Transactional
    public DeviceImage upload(String deviceCode, MultipartFile file) {
        validate(file);

        String bucket = awsS3Properties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AWS S3 bucket is not configured");
        }

        String originalFileName = safeFileName(file.getOriginalFilename());
        String contentType = file.getContentType();
        String key = buildS3Key(deviceCode, originalFileName);

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

        DeviceImage image = new DeviceImage();
        image.setDeviceCode(deviceCode);
        image.setOriginalFileName(originalFileName);
        image.setContentType(contentType);
        image.setFileSize(file.getSize());
        image.setS3Bucket(bucket);
        image.setS3Key(key);
        image.setS3Url("s3://" + bucket + "/" + key);

        return deviceImageRepository.save(image);
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

    private String buildS3Key(String deviceCode, String originalFileName) {
        String prefix = awsS3Properties.getPrefix();
        String normalizedPrefix = prefix == null ? "" : prefix.strip().replaceAll("^/+|/+$", "");
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String fileName = timestamp + "-" + UUID.randomUUID() + "-" + originalFileName;

        if (normalizedPrefix.isBlank()) {
            return deviceCode + "/" + fileName;
        }

        return normalizedPrefix + "/" + deviceCode + "/" + fileName;
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "gazebo-screenshot.png";
        }

        return originalFileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
