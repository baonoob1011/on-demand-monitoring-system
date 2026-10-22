package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.media.domain.UploadFailureEvent;
import com.ondemandmonitoring.media.enums.UploadFailureCategory;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Current fresh-schema gate, not an incremental/historical rollout fixture. */
class CurrentSchemaPostgresTest extends ChecklistPostgresTest {
    @Test void hibernateCreatesUuidMediaIdAndUploadFailureEventRoundTrips() {
        var jdbc = new JdbcTemplate(dataSource);
        String mediaIdType = jdbc.queryForObject("""
                select format_type(a.atttypid, a.atttypmod)
                from pg_attribute a
                where a.attrelid = ?::regclass and a.attname = 'media_id' and not a.attisdropped
                """, String.class, SCHEMA + ".media_upload_failure_events");
        assertEquals("uuid", mediaIdType,
                "Hibernate-created schema must match the UUID media id contract before bootstrap");

        String mediaId = UUID.randomUUID().toString();
        UploadFailureEvent event = new UploadFailureEvent();
        event.setEventKey("upload-failure:" + UUID.randomUUID());
        event.setMissionId(UUID.randomUUID().toString());
        event.setMediaId(mediaId);
        event.setAttemptId(UUID.randomUUID().toString());
        event.setAttemptNumber(1);
        event.setCategory(UploadFailureCategory.TRANSFER_FAILED);
        event.setOccurredAt(Instant.now());

        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            entityManager.persist(event);
            entityManager.flush();
            String eventId = event.getId();
            entityManager.getTransaction().commit();

            assertEquals(mediaId, jdbc.queryForObject("""
                    select media_id::text from media_upload_failure_events where id = ?
                    """, String.class, eventId));
            assertEquals(mediaId, entityManager.find(UploadFailureEvent.class, eventId).getMediaId());
        } finally {
            if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
            entityManager.close();
        }
    }

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

    @Test void evidencePolicyMigrationPreservesV0V1AndSupportsV2OnExistingSchema() {
        bootstrapCurrentSchema();
        var jdbc = new JdbcTemplate(dataSource);
        String orderId = orderService.createOrder(orderRequest(service("Evidence policy migration fixture"))).getId();
        jdbc.update("""
                insert into order_checklist_items
                    (id,created_at,version,order_id,content,display_order,source_type,review_status,
                     evidence_policy_version,minimum_evidence_count,required_evidence_type)
                values ('v0',now(),0,?,'V0',0,'CUSTOMER_CUSTOM','PENDING',0,0,'IMAGE_OR_VIDEO'),
                       ('v1',now(),0,?,'V1',1,'CUSTOMER_CUSTOM','PENDING',1,1,'IMAGE_OR_VIDEO')
                """, orderId, orderId);
        jdbc.execute("alter table " + SCHEMA + ".order_checklist_items alter column required_evidence_type drop not null");
        jdbc.execute("alter table " + SCHEMA + ".order_checklist_items drop constraint ck_snapshot_evidence_policy");
        jdbc.update("update " + SCHEMA + ".order_checklist_items set required_evidence_type=null where id in ('v0','v1')");
        CurrentSchemaPostgres.migrateEvidenceRequirementsV2(dataSource, SCHEMA);
        // Deployments upgrade V2 -> flexible pricing/evidence V3 in order; later tests need the current constraint again.
        CurrentSchemaPostgres.migrateFlexibleChecklistPricing(dataSource, SCHEMA);
        assertEquals("IMAGE_OR_VIDEO", jdbc.queryForObject("select required_evidence_type from " + SCHEMA + ".order_checklist_items where id='v0'", String.class));
        assertEquals(0, jdbc.queryForObject("select minimum_evidence_count from " + SCHEMA + ".order_checklist_items where id='v0'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select evidence_policy_version from " + SCHEMA + ".order_checklist_items where id='v1'", Integer.class));
        jdbc.update("""
                insert into order_checklist_items
                    (id,created_at,version,order_id,content,display_order,source_type,review_status,
                     evidence_policy_version,minimum_evidence_count,required_evidence_type)
                values ('v2',now(),0,?,'V2',2,'CUSTOMER_CUSTOM','PENDING',2,3,'VIDEO')
                """, orderId);
        verifyCurrentSchema();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into " + SCHEMA + ".order_checklist_items(id,created_at,version,order_id,content,display_order,source_type,review_status,evidence_policy_version,minimum_evidence_count,required_evidence_type) values ('bad-v2',now(),0,'legacy-order','Bad',3,'CUSTOMER_CUSTOM','PENDING',2,0,'IMAGE')"));
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
