package com.ondemandmonitoring.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(AwsS3Properties.class)
@Slf4j
public class AwsS3Config {

    @Bean
    public S3Client s3Client(Environment environment, AwsS3Properties properties) {
        String region = environment.getProperty("aws.region");
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("AWS region is not configured. Set AWS_REGION to the S3 bucket region.");
        }

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider(properties))
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(Environment environment, AwsS3Properties properties) {
        String region = environment.getProperty("aws.region");
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("AWS region is not configured. Set AWS_REGION to the S3 bucket region.");
        }

        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider(properties))
                .build();
    }

    private AwsCredentialsProvider credentialsProvider(AwsS3Properties properties) {
        String accessKey = properties.getAccessKeyBao();
        String secretKey = properties.getSecretKeyBao();

        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            log.info("[S3-CONFIG] credentialSource=aws.s3.access-key-bao accessKey={}", maskAccessKey(accessKey));
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }

        log.info("[S3-CONFIG] credentialSource=DefaultCredentialsProvider accessKey=default-chain");
        return DefaultCredentialsProvider.create();
    }

    private String maskAccessKey(String accessKey) {
        if (!StringUtils.hasText(accessKey)) {
            return "blank";
        }
        String trimmed = accessKey.trim();
        if (trimmed.length() <= 8) {
            return "***";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }
}
