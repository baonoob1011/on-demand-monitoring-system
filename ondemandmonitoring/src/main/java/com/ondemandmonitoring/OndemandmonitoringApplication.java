package com.ondemandmonitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class OndemandmonitoringApplication {

    private static final Logger log = LoggerFactory.getLogger(OndemandmonitoringApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(OndemandmonitoringApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logSwaggerUrl(ApplicationReadyEvent event) {
        Environment environment = event.getApplicationContext().getEnvironment();
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String swaggerPath = environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html");

        log.info("Swagger UI: http://localhost:{}{}{}", port, contextPath, swaggerPath);
    }
}
