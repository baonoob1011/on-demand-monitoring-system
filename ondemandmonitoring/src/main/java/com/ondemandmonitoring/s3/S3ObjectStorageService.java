package com.ondemandmonitoring.s3;

import java.io.InputStream;
import java.time.Duration;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class S3ObjectStorageService {

    private static final long PRESIGNED_URL_EXPIRES_SECONDS = 900;

    S3Client s3Client;
    S3Presigner s3Presigner;
    AwsS3Properties awsS3Properties;

    public StoredObject put(
            String key,
            String contentType,
            long contentLength,
            InputStream inputStream,
            String diagnosticPrefix) {
        String bucket = awsS3Properties.getBucket();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();


        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
        return new StoredObject(bucket, key, "s3://" + bucket + "/" + key);
    }

    public StoredObjectStream open(String bucket, String key) {
        ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        GetObjectResponse response = inputStream.response();
        return new StoredObjectStream(inputStream, response.contentLength(), response.contentType());
    }

    public String createPresignedGetUrl(String bucket, String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGNED_URL_EXPIRES_SECONDS))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public void deleteQuietly(String bucket, String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException exception) {
            log.warn("Failed to cleanup S3 object. bucket={}, key={}", bucket, key, exception);
        }
    }

    public String bucket() {
        return awsS3Properties.getBucket();
    }

    public long presignedUrlExpiresSeconds() {
        return PRESIGNED_URL_EXPIRES_SECONDS;
    }

    private String keyPrefix(String key) {
        int lastSlash = key.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return key.substring(0, lastSlash + 1);
    }

    public record StoredObject(String bucket, String key, String url) {}

    public record StoredObjectStream(InputStream inputStream, Long contentLength, String contentType) {}
}
