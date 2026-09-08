package com.ondemandmonitoring.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Global OpenAPI configuration for the
 * On-Demand Monitoring System (OMSS).
 *
 * <p>This configuration defines the metadata, environments,
 * and authentication mechanism used by the REST API documentation.</p>
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(apiServers())
                .components(apiComponents())
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(SECURITY_SCHEME_NAME)
                );
    }

    private Info apiInfo() {
        return new Info()
                .title("On-Demand Monitoring System (OMSS) API")
                .version("1.0.0")
                .description("""
                        RESTful API specification for the On-Demand Monitoring System (OMSS).

                        OMSS provides an end-to-end platform for managing on-demand
                        monitoring missions using UAV/drone devices equipped with
                        camera and telemetry capabilities.

                        ## API Design

                        The API follows RESTful principles and uses standardized
                        response structures and centralized exception handling.
                        """)
                .contact(new Contact()
                        .name("OMSS Development Team")
                        .email("dev-support@ondemandmonitoring.com")
                        .url("https://ondemandmonitoring.com"))
                .license(new License()
                        .name("Apache License 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    private List<Server> apiServers() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Local Development Environment"),

                new Server()
                        .url("https://api-staging.ondemandmonitoring.com")
                        .description("Staging Environment")
        );
    }

    private Components apiComponents() {
        return new Components()
                .addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                        "JWT access token obtained from the "
                                                + "OMSS Identity / Authentication Service."
                                )
                );
    }
}