package com.ondemandmonitoring.checklist;

import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, random disposable schema in a dedicated local verification database only. */
@EnabledIfEnvironmentVariable(named="ODMS_CHECKLIST_TEST_JDBC_URL", matches="jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/odms_checklist_verification")
class ChecklistEvidencePostgresTest {
    String schema;
    Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("ODMS_CHECKLIST_TEST_JDBC_URL"),
                System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_USER","checklist_test"),
                System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_PASSWORD","checklist_test_only"));
    }
    void sql(String statement) throws SQLException { try(var c=connection(); var s=c.createStatement()) { s.execute(statement); } }
    @BeforeEach void prepare() throws Exception {
        schema="evidence_verify_"+UUID.randomUUID().toString().replace("-","");
        sql("CREATE SCHEMA "+schema);
        sql("CREATE TABLE "+schema+".users(id varchar(255) PRIMARY KEY)");
        sql("CREATE TABLE "+schema+".order_checklist_items(id varchar(255) PRIMARY KEY)");
        sql("CREATE TABLE "+schema+".mission_checklist_executions(id varchar(255) PRIMARY KEY, mission_id varchar(255) NOT NULL, version bigint NOT NULL DEFAULT 0)");
        sql("CREATE TABLE "+schema+".drone_media(id varchar(255) PRIMARY KEY, mission_id varchar(255) NOT NULL)");
        sql("INSERT INTO "+schema+".order_checklist_items VALUES ('old')");
        migrate();
        sql("INSERT INTO "+schema+".users VALUES ('actor')");
        sql("INSERT INTO "+schema+".mission_checklist_executions(id,mission_id) VALUES ('e','m'),('e2','m'),('other','other')");
        sql("INSERT INTO "+schema+".drone_media VALUES ('a','m'),('a2','m'),('wrong','other')");
    }
    void migrate() throws Exception {
        try(var stream=getClass().getClassLoader().getResourceAsStream("seeddata/migrations/add_mission_checklist_evidence.sql")) {
            assertNotNull(stream); sql(new String(stream.readAllBytes(),StandardCharsets.UTF_8).replace("public.",schema+"."));
        }
    }
    @AfterEach void cleanup() throws SQLException { if(schema!=null && schema.matches("evidence_verify_[a-f0-9]{32}")) sql("DROP SCHEMA "+schema+" CASCADE"); }
    String insert(String id,String execution,String media,String mission) {
        return "INSERT INTO "+schema+".mission_checklist_evidence(id,mission_id,mission_checklist_execution_id,media_asset_id,attached_by,attached_at,created_at,version) VALUES ('"+id+"','"+mission+"','"+execution+"','"+media+"','actor',now(),now(),0)";
    }
    long scalar(String statement) throws SQLException { try(var c=connection();var s=c.createStatement();var r=s.executeQuery(statement)) { r.next();return r.getLong(1); } }
    @Test void migrationPreservesHistoricalPolicyAndSupportsV1AndRerun() throws Exception {
        assertEquals(0,scalar("SELECT evidence_policy_version+minimum_evidence_count FROM "+schema+".order_checklist_items WHERE id='old'"));
        sql("INSERT INTO "+schema+".order_checklist_items VALUES ('new',1,1)"); migrate();
        assertEquals(2,scalar("SELECT evidence_policy_version+minimum_evidence_count FROM "+schema+".order_checklist_items WHERE id='new'"));
    }
    @Test void compositeFksRejectCrossMissionExecutionAndMedia() throws Exception {
        assertEquals("23503",assertThrows(SQLException.class,()->sql(insert("x","other","a","m"))).getSQLState());
        assertEquals("23503",assertThrows(SQLException.class,()->sql(insert("x","e","wrong","m"))).getSQLState());
    }
    @Test void duplicateActivePairRejectedButReattachPreservesHistory() throws Exception {
        sql(insert("first","e","a","m")); assertEquals("23505",assertThrows(SQLException.class,()->sql(insert("second","e","a","m"))).getSQLState());
        sql("UPDATE "+schema+".mission_checklist_evidence SET detached_at=now(),detached_by='actor' WHERE id='first'");
        sql(insert("second","e","a","m")); assertEquals(2,scalar("SELECT count(*) FROM "+schema+".mission_checklist_evidence"));
    }
    @Test void manyToManyAndMediaDeleteRestriction() throws Exception {
        sql(insert("one","e","a","m")); sql(insert("two","e2","a","m")); sql(insert("three","e","a2","m"));
        assertEquals(3,scalar("SELECT count(*) FROM "+schema+".mission_checklist_evidence"));
        assertEquals("23503",assertThrows(SQLException.class,()->sql("DELETE FROM "+schema+".drone_media WHERE id='a'")).getSQLState());
    }
    @Test void concurrentDuplicateInsertHasExactlyOneWinner() throws Exception {
        var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> command=()->{barrier.await(10,TimeUnit.SECONDS);try{sql(insert(UUID.randomUUID().toString(),"e","a","m"));return true;}catch(SQLException ex){assertEquals("23505",ex.getSQLState());return false;}};
            var first=pool.submit(command);var second=pool.submit(command);
            assertNotEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            assertEquals(1,scalar("SELECT count(*) FROM "+schema+".mission_checklist_evidence"));
        } finally { pool.shutdownNow(); }
    }
    @Test void optimisticVersionAndRequiredIndexesExist() throws Exception {
        sql("UPDATE "+schema+".mission_checklist_executions SET version=1 WHERE id='e' AND version=0");
        try(var c=connection();var s=c.createStatement()) { assertEquals(0,s.executeUpdate("UPDATE "+schema+".mission_checklist_executions SET version=2 WHERE id='e' AND version=0")); }
        assertEquals(3,scalar("SELECT count(*) FROM pg_indexes WHERE schemaname='"+schema+"' AND indexname IN ('uk_evidence_active_pair','ix_evidence_media','ix_evidence_mission_execution')"));
    }
}
