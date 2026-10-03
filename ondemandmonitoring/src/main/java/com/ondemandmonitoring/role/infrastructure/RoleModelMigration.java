package com.ondemandmonitoring.role.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Migrates legacy role codes through JDBC before any enum-backed Role entities are loaded. */
@Component
@Order(5)
@RequiredArgsConstructor
public class RoleModelMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        String sql = new ClassPathResource("seeddata/migrations/migrate_four_system_roles.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }
}
