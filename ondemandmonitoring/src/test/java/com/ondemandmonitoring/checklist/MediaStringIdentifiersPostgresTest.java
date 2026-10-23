package com.ondemandmonitoring.checklist;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Rehearses the real historical migration and its upgrade in a disposable PostgreSQL schema. */
@EnabledIfEnvironmentVariable(named = "ODMS_CHECKLIST_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/odms_checklist_verification")
class MediaStringIdentifiersPostgresTest {
    @Test void freshHibernateSchemaBootstrapsAndStringEventRoundTrips() {
        String schema = "checklist_verify_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = new DriverManagerDataSource(System.getenv("ODMS_CHECKLIST_TEST_JDBC_URL"),
                System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_USER", "checklist_test"),
                System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_PASSWORD", "checklist_test_only"));
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + schema);
        var factory = new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        try {
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.ondemandmonitoring");
            factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "create",
                    "hibernate.default_schema", schema));
            factory.afterPropertiesSet();
            for (String table : List.of("drone_media", "media_upload_attempts", "manual_upload_tasks",
                    "media_audit_logs", "media_notification_outbox", "media_storage_event_inbox", "media_upload_failure_events")) {
                assertEquals("character varying(255)", jdbc.queryForObject(
                        "select format_type(atttypid,atttypmod) from pg_attribute where attrelid=?::regclass and attname='id'",
                        String.class, schema + "." + table));
            }
            CurrentSchemaPostgres.bootstrap(dataSource, schema);
            CurrentSchemaPostgres.verify(dataSource, schema);
            try (var em = factory.getObject().createEntityManager()) {
                var event = new com.ondemandmonitoring.media.domain.UploadFailureEvent();
                event.setEventKey("upload-failure:string-contract");
                event.setMissionId("mission-string"); event.setMediaId("media-string"); event.setAttemptId("attempt-string");
                event.setAttemptNumber(1);
                event.setCategory(com.ondemandmonitoring.media.enums.UploadFailureCategory.TRANSFER_FAILED);
                event.setOccurredAt(java.time.Instant.now()); event.setCreatedAt(java.time.Instant.now());
                em.getTransaction().begin(); em.persist(event); em.getTransaction().commit(); em.clear();
                assertEquals("media-string", em.find(com.ondemandmonitoring.media.domain.UploadFailureEvent.class, event.getId()).getMediaId());
            }
        } finally {
            if (factory.getObject() != null) factory.destroy();
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test void migrationPreservesHistoryConstraintsAndIndexesAndCanBeReplayed() {
        String schema = "checklist_verify_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = new DriverManagerDataSource(System.getenv("ODMS_CHECKLIST_TEST_JDBC_URL"),
                System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_USER", "checklist_test"),
                System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_PASSWORD", "checklist_test_only"));
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            jdbc.execute(CurrentSchemaPostgres.sql("add_media_upload_failure_events.sql", schema));
            String table = schema + ".media_upload_failure_events";
            jdbc.execute("insert into " + table + " (id,event_key,mission_id,media_id,attempt_id,attempt_number,category,occurred_at,created_at,version)"
                    + " values ('00000000-0000-0000-0000-000000000001','upload-failure:fixture',"
                    + "'00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003',"
                    + "'00000000-0000-0000-0000-000000000004',1,'TRANSFER_FAILED',now(),now(),0)");
            var before = jdbc.queryForList("select id::text,mission_id::text,media_id::text,attempt_id::text,event_key,occurred_at,created_at,version from " + table);
            String migration = CurrentSchemaPostgres.sql("normalize_media_upload_failure_string_ids.sql", schema);
            jdbc.execute(migration);
            jdbc.execute(migration);
            assertEquals(before, jdbc.queryForList("select id,mission_id,media_id,attempt_id,event_key,occurred_at,created_at,version from " + table));
            for (String column : List.of("id", "mission_id", "media_id", "attempt_id")) {
                assertEquals("character varying(255)", jdbc.queryForObject(
                        "select format_type(atttypid,atttypmod) from pg_attribute where attrelid=?::regclass and attname=?",
                        String.class, table, column));
            }
            assertEquals(4, jdbc.queryForObject("select count(*) from pg_constraint where conrelid=?::regclass", Integer.class, table));
            assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where schemaname=? and indexname='ix_upload_failure_pending' and indexdef like '%WHERE (processed_at IS NULL)%'", Integer.class, schema));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.execute(
                    "insert into " + table + " select 'duplicate',event_key,mission_id,media_id,attempt_id,attempt_number,category,occurred_at,failure_code,failure_message,processed_at,processing_outcome,created_at,updated_at,version from " + table));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.execute("update " + table + " set category='INVALID'"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.execute("update " + table + " set media_id=null"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.execute("update " + table + " set processed_at=now()"));
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
