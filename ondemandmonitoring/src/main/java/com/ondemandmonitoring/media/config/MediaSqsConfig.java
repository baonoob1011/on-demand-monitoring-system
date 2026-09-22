package com.ondemandmonitoring.media.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class MediaSqsConfig {
    @Bean
    @ConditionalOnProperty(name = "app.media.sqs.enabled", havingValue = "true")
    SqsClient mediaSqsClient(Environment environment, AwsCredentialsProvider credentials) {
        String queueUrl = environment.getRequiredProperty("app.media.sqs.queue-url");
        if (queueUrl.isBlank()) {
            throw new IllegalStateException("MEDIA_SQS_QUEUE_URL is required when MEDIA_SQS_ENABLED=true");
        }
        return SqsClient.builder().region(Region.of(environment.getRequiredProperty("aws.region")))
                .credentialsProvider(credentials).build();
    }
}
