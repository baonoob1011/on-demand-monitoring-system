package com.ondemandmonitoring.controlgateway;

import com.ondemandmonitoring.controlgateway.config.ControlGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ControlGatewayProperties.class)
public class ControlGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlGatewayApplication.class, args);
    }
}
