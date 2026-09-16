package com.ondemandmonitoring.controlgateway.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel flightControllerChannel(ControlGatewayProperties properties) {
        ControlGatewayProperties.FlightController target = properties.flightController();
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                .forAddress(target.host(), target.port())
                .keepAliveTime(20, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true);
        if (!target.tls()) {
            builder.usePlaintext();
        }
        return builder.build();
    }
}
