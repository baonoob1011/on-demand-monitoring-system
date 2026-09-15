package com.ondemandmonitoring.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@Configuration
@Slf4j
public class AwsCredentialsConfig {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(Environment environment) {
        String accessKey = environment.getProperty("AWS_ACCESS_KEY_ID");
        String secretKey = environment.getProperty("AWS_SECRET_ACCESS_KEY");
        boolean hasAccessKey = StringUtils.hasText(accessKey);
        boolean hasSecretKey = StringUtils.hasText(secretKey);

        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException(
                    "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY must be configured together");
        }
        if (hasAccessKey) {
            log.info("[AWS-CONFIG] credentialSource=environment");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }

        log.info("[AWS-CONFIG] credentialSource=default-provider-chain");
        return DefaultCredentialsProvider.create();
    }
}
