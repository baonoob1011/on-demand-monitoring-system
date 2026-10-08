package com.ondemandmonitoring.categoryservice.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CategoryServiceDataInitializer {

    @Bean
    ApplicationRunner seedCategoryServices() {
        return args -> {
            // ServiceCatalogSeedDataInitializer is the single source of truth
            // for demo service catalog data.
        };
    }
}
