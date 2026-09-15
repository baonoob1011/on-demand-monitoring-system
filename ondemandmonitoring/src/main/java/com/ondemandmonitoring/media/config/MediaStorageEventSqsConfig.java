package com.ondemandmonitoring.media.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@EnableConfigurationProperties(MediaStorageEventSqsProperties.class)
@ConditionalOnProperty(prefix = "app.media.storage-event-sqs", name = "enabled", havingValue = "true")
public class MediaStorageEventSqsConfig {

    @Bean
    public SqsClient mediaStorageEventSqsClient(
            Environment environment, AwsCredentialsProvider credentialsProvider) {
        String region = environment.getProperty("aws.region");
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("AWS_REGION is required when the media SQS consumer is enabled");
        }
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
    }
}
