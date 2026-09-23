package com.ondemandmonitoring.order.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSchemaFixer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        alterColumnToText("description");
        alterColumnToText("address");
        alterColumnToText("reject_reason");
    }

    private void alterColumnToText(String columnName) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE IF EXISTS orders ALTER COLUMN "
                            + columnName
                            + " TYPE TEXT"
            );
        } catch (Exception exception) {
            log.warn(
                    "Could not widen orders.{} to TEXT",
                    columnName,
                    exception
            );
        }
    }
}
