package com.ondemandmonitoring.checklist;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Representative pre-expansion rows in random schemas of the dedicated verification DB only. */
@EnabledIfEnvironmentVariable(named = "ODMS_CHECKLIST_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/odms_checklist_verification")
class OrderMultiMissionFoundationPostgresTest {
    private String schema;

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("ODMS_CHECKLIST_TEST_JDBC_URL"),
                System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_USER", "checklist_test"),
                System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_PASSWORD", "checklist_test_only"));
    }

    private void sql(String statement) throws SQLException {
        try (var connection = connection(); var command = connection.createStatement()) {
            command.execute(statement.replace("public.", schema + "."));
        }
    }

    private String resource(String file) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("seeddata/migrations/" + file)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void migrate() throws Exception { sql(resource("add_order_multi_mission_foundation.sql")); }

    private List<String> rows(String query) throws SQLException {
        try (var connection = connection(); var command = connection.createStatement();
                var result = command.executeQuery(query.replace("public.", schema + "."))) {
            var values = new ArrayList<String>();
            while (result.next()) values.add(result.getString(1));
            return values;
        }
    }

    private String value(String query) throws SQLException { return rows(query).getFirst(); }

    @BeforeEach void prepare() throws Exception {
        schema = "foundation_verify_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE SCHEMA " + schema);
        sql("""
                CREATE TABLE public.users(id varchar(255) PRIMARY KEY);
                CREATE TABLE public.orders(id varchar(255) PRIMARY KEY, order_status varchar(30) NOT NULL,
                    point geometry(Point,4326), target_area geometry(Polygon,4326));
                CREATE TABLE public.missions(id varchar(255) PRIMARY KEY, order_id varchar(255) NOT NULL REFERENCES public.orders(id),
                    status varchar(40) NOT NULL, scheduled_start_at timestamptz NOT NULL, scheduled_end_at timestamptz NOT NULL,
                    UNIQUE(order_id));
                CREATE TABLE public.order_checklist_items(id varchar(255) PRIMARY KEY, order_id varchar(255) NOT NULL REFERENCES public.orders(id),
                    display_order integer NOT NULL, content varchar(500) NOT NULL);
                CREATE TABLE public.mission_results(id varchar(255) PRIMARY KEY, mission_id varchar(255) NOT NULL UNIQUE REFERENCES public.missions(id),
                    approval_status varchar(40) NOT NULL, submitted_at timestamptz);
                CREATE TABLE public.drone_media(id varchar(255) PRIMARY KEY, mission_id varchar(255) NOT NULL REFERENCES public.missions(id),
                    media_status varchar(40), source_type varchar(40), validated_at timestamptz);
                """);
        sql(resource("add_mission_checklist_executions.sql"));
        sql(resource("add_mission_checklist_evidence.sql"));
        sql("""
                INSERT INTO public.users VALUES ('actor');
                INSERT INTO public.orders VALUES
                    ('o','IN_PROGRESS',ST_SetSRID(ST_MakePoint(106.7,10.8),4326),ST_GeomFromText('POLYGON((106.6 10.7,106.8 10.7,106.8 10.9,106.6 10.9,106.6 10.7))',4326)),
                    ('other','APPROVED',NULL,NULL),('pending','PENDING',NULL,NULL),('rejected','REJECTED',NULL,NULL),
                    ('approved','APPROVED',NULL,NULL),('done','COMPLETED',ST_SetSRID(ST_MakePoint(106.7,10.8),4326),NULL);
                INSERT INTO public.missions VALUES
                    ('m','o','IN_FLIGHT',now(),now()+interval '1 hour'),
                    ('m2','other','RESOURCE_ASSIGNING',now(),now()+interval '1 hour'),
                    ('md','done','COMPLETED',now(),now()+interval '1 hour');
                INSERT INTO public.order_checklist_items VALUES
                    ('legacy','o',0,'Legacy',0,0),('new','o',1,'V1',1,1),('foreign','other',0,'Other',0,0);
                INSERT INTO public.mission_checklist_executions
                    (id,created_at,updated_at,version,mission_id,order_id,order_checklist_item_id,execution_status,assessment_status,
                     observation,unable_to_verify_reason,started_at,completed_at,last_modified_by) VALUES
                    ('e',now(),now(),7,'m','o','legacy','COMPLETED','NON_COMPLIANT','Keep observation',NULL,now(),now(),'actor'),
                    ('e2',now(),NULL,4,'m','o','new','UNABLE_TO_VERIFY','NOT_ASSESSED',NULL,'Keep reason',now(),now(),NULL);
                INSERT INTO public.drone_media VALUES ('asset','m','AVAILABLE','DRONE_CAMERA',now());
                INSERT INTO public.mission_checklist_evidence
                    (id,mission_id,mission_checklist_execution_id,media_asset_id,attached_by,attached_at,created_at,version)
                    VALUES ('link','m','e','asset','actor',now(),now(),2);
                INSERT INTO public.mission_results VALUES ('r','m','DRAFT',NULL),('rd','md','APPROVED',now());
                """);
    }

    @AfterEach void cleanup() throws SQLException {
        if (schema != null && schema.matches("foundation_verify_[a-f0-9]{32}")) sql("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test void backfillPreservesEveryHistoricalValueAndIsRerunnable() throws Exception {
        var before = new HashMap<String, List<String>>();
        for (String table : List.of("mission_checklist_executions", "mission_checklist_evidence", "drone_media", "mission_results", "order_checklist_items"))
            before.put(table, rows("SELECT to_jsonb(t)::text FROM public." + table + " t ORDER BY id"));
        migrate();
        assertEquals("2", value("SELECT count(*) FROM public.mission_checklist_assignments"));
        assertEquals("0", value("SELECT count(*) FROM public.mission_checklist_executions WHERE mission_checklist_assignment_id IS NULL"));
        assertEquals(before.get("mission_checklist_executions"), rows("SELECT (to_jsonb(t)-'mission_checklist_assignment_id')::text FROM public.mission_checklist_executions t ORDER BY id"));
        for (String table : List.of("mission_checklist_evidence", "drone_media", "mission_results", "order_checklist_items"))
            assertEquals(before.get(table), rows("SELECT to_jsonb(t)::text FROM public." + table + " t ORDER BY id"));
        assertEquals("2", value("SELECT count(*) FROM public.mission_checklist_assignments a JOIN public.mission_checklist_executions e ON e.mission_checklist_assignment_id=a.id WHERE a.assigned_at=e.created_at AND a.assigned_by IS NULL"));
        var assignments = rows("SELECT to_jsonb(a)::text FROM public.mission_checklist_assignments a ORDER BY id");
        migrate();
        assertEquals(assignments, rows("SELECT to_jsonb(a)::text FROM public.mission_checklist_assignments a ORDER BY id"));
        assertEquals("1", value("SELECT count(*) FROM public.mission_checklist_evidence ev JOIN public.mission_checklist_executions e ON e.id=ev.mission_checklist_execution_id JOIN public.mission_checklist_assignments a ON a.id=e.mission_checklist_assignment_id WHERE ev.mission_id=a.mission_id"));
    }

    @Test void scopeIsCopiedWithoutInventingLabelAndCustomScopeSurvivesRerun() throws Exception {
        migrate();
        assertEquals("true", value("SELECT (ST_Equals(m.target_point,o.point) AND ST_Equals(m.target_area,o.target_area))::text FROM public.missions m JOIN public.orders o ON o.id=m.order_id WHERE m.id='m'"));
        assertEquals("0", value("SELECT count(*) FROM public.missions WHERE scope_label IS NOT NULL OR finalized_plan_revision IS NOT NULL"));
        sql("UPDATE public.missions SET target_point=ST_SetSRID(ST_MakePoint(106.71,10.81),4326),target_area=NULL WHERE id='m'");
        migrate();
        assertEquals("106.71", value("SELECT ST_X(target_point) FROM public.missions WHERE id='m'"));
        assertEquals("0", value("SELECT count(*) FROM public.missions WHERE id='m' AND target_area IS NOT NULL"));
    }

    @Test void planClassificationIsReadOnlyAndDoesNotFabricateFinalization() throws Exception {
        migrate();
        assertEquals("6", value("SELECT count(*) FROM public.orders WHERE execution_plan_status='DRAFT' AND execution_plan_revision=0 AND execution_plan_finalized_at IS NULL AND execution_plan_finalized_by IS NULL"));
        // Run the entire verification resource: it is SELECT-only and must leave metadata untouched.
        sql(resource("verify_order_multi_mission_foundation.sql"));
        assertEquals("6", value("SELECT count(*) FROM public.orders WHERE execution_plan_status='DRAFT'"));
        sql("INSERT INTO public.orders(id,order_status) VALUES ('new-order','PENDING')");
        assertEquals("DRAFT:0", value("SELECT execution_plan_status || ':' || execution_plan_revision FROM public.orders WHERE id='new-order'"));
        assertEquals("23514", assertThrows(SQLException.class, () -> sql("UPDATE public.orders SET execution_plan_revision=-1 WHERE id='o'")).getSQLState());
        try (var connection = connection(); var command = connection.createStatement()) {
            boolean hasResult = command.execute(resource("verify_order_multi_mission_foundation.sql").replace("public.", schema + "."));
            var recommendations = new HashMap<String, String>();
            while (hasResult || command.getUpdateCount() != -1) {
                if (hasResult) try (var result = command.getResultSet()) {
                    var metadata = result.getMetaData();
                    if (metadata.getColumnLabel(metadata.getColumnCount()).equals("recommended_action")) {
                        while (result.next()) recommendations.put(result.getString("order_id"), result.getString("recommended_action"));
                    }
                }
                hasResult = command.getMoreResults();
            }
            assertEquals("KEEP_DRAFT", recommendations.get("pending"));
            assertEquals("KEEP_DRAFT", recommendations.get("rejected"));
            assertEquals("KEEP_DRAFT", recommendations.get("approved"));
            assertEquals("CANDIDATE_FINALIZED_1_REVIEW_SCOPE", recommendations.get("o"));
            assertEquals("CANDIDATE_FINALIZED_1_REVIEW_EVIDENCE_AND_SCOPE", recommendations.get("done"));
        }
    }

    @Test void activeUniquenessAndSoftRemovalKeepPlanningHistory() throws Exception {
        migrate();
        String insert = "INSERT INTO public.mission_checklist_assignments(id,mission_id,order_id,order_checklist_item_id,display_order,assigned_at,created_at,version) VALUES ('next','m2','other','foreign',0,now(),now(),0)";
        sql(insert);
        assertEquals("23505", assertThrows(SQLException.class, () -> sql(insert.replace("'next'", "'duplicate'"))).getSQLState());
        sql("UPDATE public.mission_checklist_assignments SET removed_at=now(),removed_by='actor',removal_reason='Planning correction' WHERE id='next'");
        sql(insert.replace("'next'", "'replacement'"));
        assertEquals("2", value("SELECT count(*) FROM public.mission_checklist_assignments WHERE mission_id='m2'"));
        assertEquals("23514", assertThrows(SQLException.class, () -> sql("UPDATE public.mission_checklist_assignments SET display_order=-1 WHERE id='replacement'")).getSQLState());
        assertEquals("23514", assertThrows(SQLException.class, () -> sql("UPDATE public.mission_checklist_assignments SET removed_by='actor' WHERE id='replacement'")).getSQLState());
    }

    @Test void sameOrderAndExecutionIdentityAreProtectedWhileOldRuntimeCanStillInsertNullLink() throws Exception {
        migrate();
        assertEquals("23503", assertThrows(SQLException.class, () -> sql("INSERT INTO public.mission_checklist_assignments(id,mission_id,order_id,order_checklist_item_id,display_order,assigned_at,created_at,version) VALUES ('cross','m','o','foreign',0,now(),now(),0)")).getSQLState());
        sql("INSERT INTO public.mission_checklist_assignments(id,mission_id,order_id,order_checklist_item_id,display_order,assigned_at,created_at,version) VALUES ('wrong-target','m2','other','foreign',0,now(),now(),0)");
        String wrong = "wrong-target";
        assertEquals("23503", assertThrows(SQLException.class, () -> sql("UPDATE public.mission_checklist_executions SET mission_checklist_assignment_id='" + wrong + "' WHERE id='e'")).getSQLState());
        sql("INSERT INTO public.mission_checklist_executions(id,created_at,version,mission_id,order_id,order_checklist_item_id,execution_status,assessment_status) VALUES ('new-runtime',now(),0,'m2','other','foreign','PENDING','NOT_ASSESSED')");
        assertEquals("1", value("SELECT count(*) FROM public.mission_checklist_executions WHERE mission_checklist_assignment_id IS NULL"));
        migrate();
        assertEquals("0", value("SELECT count(*) FROM public.mission_checklist_executions WHERE mission_checklist_assignment_id IS NULL"));
    }

    @Test void bothLegacyOneToOneGatesRemainAndCommandMetadataIsValidated() throws Exception {
        migrate();
        // The real local schema uses missions_order_id_key, not the application's named constraint.
        assertEquals("1", value("SELECT count(*) FROM pg_constraint WHERE conrelid='" + schema + ".missions'::regclass AND contype='u' AND conkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid='" + schema + ".missions'::regclass AND attname='order_id')]"));
        assertEquals("23505", assertThrows(SQLException.class, () -> sql("INSERT INTO public.missions(id,order_id,status,scheduled_start_at,scheduled_end_at) VALUES ('extra','o','RESOURCE_ASSIGNING',now(),now()+interval '1 hour')")).getSQLState());
        assertEquals("23505", assertThrows(SQLException.class, () -> sql("INSERT INTO public.mission_checklist_executions(id,created_at,version,mission_id,order_id,order_checklist_item_id,execution_status,assessment_status) VALUES ('duplicate',now(),0,'m','o','legacy','PENDING','NOT_ASSESSED')")).getSQLState());
        assertEquals("23514", assertThrows(SQLException.class, () -> sql("UPDATE public.missions SET creation_idempotency_key='key' WHERE id='m'")).getSQLState());
        assertEquals("23514", assertThrows(SQLException.class, () -> sql("UPDATE public.missions SET replaces_mission_id=id WHERE id='m'")).getSQLState());
        assertEquals("23503", assertThrows(SQLException.class, () -> sql("UPDATE public.missions SET replaces_mission_id='m2' WHERE id='m'")).getSQLState());
    }

    @Test void corruptCrossOrderSourceAbortsEntireExpansion() throws Exception {
        sql("ALTER TABLE public.mission_checklist_executions DROP CONSTRAINT fk_execution_mission_order");
        sql("ALTER TABLE public.mission_checklist_executions DROP CONSTRAINT fk_execution_item_order");
        sql("UPDATE public.mission_checklist_executions SET order_id='other' WHERE id='e'");
        var failure = assertThrows(SQLException.class, this::migrate);
        assertTrue(failure.getMessage().contains("cross-Order"));
        assertEquals("0", value("SELECT count(*) FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='orders' AND column_name='execution_plan_status'"));
        assertEquals("0", value("SELECT count(*) FROM information_schema.tables WHERE table_schema='" + schema + "' AND table_name='mission_checklist_assignments'"));
        assertEquals("7", value("SELECT version FROM public.mission_checklist_executions WHERE id='e'"));
    }
}
