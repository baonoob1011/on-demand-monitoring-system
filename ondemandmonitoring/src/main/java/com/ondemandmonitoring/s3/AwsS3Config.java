package com.ondemandmonitoring.s3;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(AwsS3Properties.class)
public class AwsS3Config {

    @Bean
    public S3Client s3Client(Environment environment, AwsCredentialsProvider credentialsProvider) {
        String region = environment.getProperty("aws.region");
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("AWS region is not configured. Set AWS_REGION to the S3 bucket region.");
        }

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(Environment environment, AwsCredentialsProvider credentialsProvider) {
        String region = environment.getProperty("aws.region");
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("AWS region is not configured. Set AWS_REGION to the S3 bucket region.");
        }

        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
