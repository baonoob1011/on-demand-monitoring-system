package com.ondemandmonitoring.configuration.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws.s3")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AwsS3Properties {

    String bucket;
    String prefix;
}
