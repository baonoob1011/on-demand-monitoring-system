package com.ondemandmonitoring.checklist;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Current fresh-schema gate, not an incremental/historical rollout fixture. */
class CurrentSchemaPostgresTest extends ChecklistPostgresTest {
    @Test void bootstrapIsRepeatableAndCurrentVerifierIsReadOnly() {
        bootstrapCurrentSchema();
        bootstrapCurrentSchema();
        verifyCurrentSchema();
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject("select count(*) from missions", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from pg_index i join pg_attribute a
                on a.attrelid=i.indrelid and a.attnum=i.indkey[0]
                where i.indrelid=?::regclass and i.indisunique and i.indnkeyatts=1 and a.attname='order_id'
                """, Integer.class, SCHEMA + ".missions"));
    }

    @Test void bootstrapRefusesLegacyOneToOneWithoutRepairingIt() throws Exception {
        rejectedSchemaChange("alter table missions add constraint fixture_old_unique unique(order_id)",
                "bootstrap_after_hibernate.sql", "Legacy global uniqueness");
        verifyCurrentSchema();
    }

    @Test void bootstrapRefusesIncorrectCurrentMainIndex() throws Exception {
        rejectedSchemaChange("drop index " + SCHEMA + ".uk_mission_device_assignment_current_main; "
                + "create index uk_mission_device_assignment_current_main on mission_device_assignments(mission_id)",
                "bootstrap_after_hibernate.sql", "Index drift");
        verifyCurrentSchema();
    }

    @Test void verifierRejectsMissingCurrentMainIndex() throws Exception {
        rejectedSchemaChange("drop index " + SCHEMA + ".uk_mission_device_assignment_current_main",
                "verify_current_schema.sql", "Index missing/invalid");
        verifyCurrentSchema();
    }

    @Test void freshBootstrapRefusesRuntimeRowsWithoutChangingThem() {
        var response = orderService.createOrder(orderRequest(service("Monitoring")));
        var failure = assertThrows(org.springframework.dao.DataAccessException.class, this::bootstrapCurrentSchema);
        assertTrue(failure.getMostSpecificCause().getMessage().contains("has runtime data"));
        assertEquals(response.getId(), orderService.getOrderById(response.getId()).getId());
        verifyCurrentSchema();
    }

    private void rejectedSchemaChange(String ddl, String resource, String reason) throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute(ddl);
                String sql = CurrentSchemaPostgres.sql(resource, SCHEMA)
                        .replace("BEGIN READ ONLY;", "").replace("BEGIN;", "").replace("COMMIT;", "");
                var failure = assertThrows(SQLException.class, () -> statement.execute(sql));
                assertTrue(failure.getMessage().contains(reason), failure.getMessage());
            } finally {
                connection.rollback();
            }
        }
    }
}
