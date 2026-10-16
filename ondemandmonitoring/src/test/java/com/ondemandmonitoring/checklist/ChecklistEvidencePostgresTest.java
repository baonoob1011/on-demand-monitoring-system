package com.ondemandmonitoring.checklist;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;

/** Canonical Hibernate + bootstrap evidence constraints; no historical SQL fixture. */
@SpringJUnitConfig(MissionChecklistCutoverPostgresTest.Beans.class)
@EnabledIfEnvironmentVariable(named="ODMS_CHECKLIST_TEST_JDBC_URL",
        matches="jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/odms_checklist_verification")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ChecklistEvidencePostgresTest {
    @Autowired ApplicationContext context;
    MissionChecklistCutoverPostgresTest fixture;
    String execution, siblingExecution, secondExecution, media, secondMedia, siblingMedia;

    @BeforeEach void prepare() {
        fixture = new MissionChecklistCutoverPostgresTest();
        context.getAutowireCapableBeanFactory().autowireBean(fixture);
        fixture.prepare(); fixture.fixture(2);
        fixture.choose(fixture.first,0,1); fixture.choose(fixture.second,0);
        fixture.finalizePlan();
        var first = fixture.runtime.getByMissionId(fixture.first).executions();
        execution = first.get(0).getId(); secondExecution = first.get(1).getId();
        siblingExecution = fixture.runtime.getByMissionId(fixture.second).executions().getFirst().getId();
        media=fixture.asset(fixture.first); secondMedia=fixture.asset(fixture.first);
        siblingMedia=fixture.asset(fixture.second);
    }

    @AfterAll void cleanup() { if (fixture != null) fixture.cleanup(); }

    String attach(String target, String asset, String mission) {
        String id=UUID.randomUUID().toString();
        fixture.jdbc.update("""
                insert into mission_checklist_evidence(id,mission_id,mission_checklist_execution_id,
                    media_asset_id,attached_by,attached_at,created_at,version)
                values (?,?,?,?,?,now(),now(),0)
                """,id,mission,target,asset,fixture.resolver.getCurrentUserId());
        return id;
    }

    @Test void snapshotPolicyAcceptsLegacyPairsAndConfigurablePositiveV2() {
        fixture.jdbc.update("update order_checklist_items set evidence_policy_version=0,minimum_evidence_count=0,required_evidence_type='IMAGE_OR_VIDEO'");
        fixture.jdbc.update("update order_checklist_items set evidence_policy_version=1,minimum_evidence_count=1,required_evidence_type='IMAGE_OR_VIDEO'");
        fixture.jdbc.update("update order_checklist_items set evidence_policy_version=2,minimum_evidence_count=3,required_evidence_type='VIDEO'");
        assertThrows(DataIntegrityViolationException.class,()->fixture.jdbc.update(
                "update order_checklist_items set evidence_policy_version=2,minimum_evidence_count=0,required_evidence_type='IMAGE'"));
        assertThrows(DataIntegrityViolationException.class,()->fixture.jdbc.update(
                "update order_checklist_items set evidence_policy_version=2,minimum_evidence_count=51,required_evidence_type='IMAGE'"));
        assertThrows(DataIntegrityViolationException.class,()->fixture.jdbc.update(
                "update order_checklist_items set evidence_policy_version=2,minimum_evidence_count=2,required_evidence_type='STREAMING'"));
        fixture.jdbc.update("update order_checklist_items set evidence_policy_version=0,minimum_evidence_count=0,required_evidence_type='IMAGE_OR_VIDEO'");
    }

    @Test void compositeFksRejectCrossMissionExecutionAndMedia() {
        assertThrows(DataIntegrityViolationException.class,()->attach(siblingExecution,media,fixture.first));
        assertThrows(DataIntegrityViolationException.class,()->attach(execution,siblingMedia,fixture.first));
    }

    @Test void duplicateActivePairRejectedButReattachPreservesHistory() {
        String first=attach(execution,media,fixture.first);
        assertThrows(DataIntegrityViolationException.class,()->attach(execution,media,fixture.first));
        fixture.jdbc.update("update mission_checklist_evidence set detached_at=now(),detached_by=? where id=?",
                fixture.resolver.getCurrentUserId(),first);
        attach(execution,media,fixture.first);
        assertEquals(2,fixture.rows("mission_checklist_evidence"));
    }

    @Test void manyToManyAndMediaDeleteRestriction() {
        attach(execution,media,fixture.first); attach(secondExecution,media,fixture.first);
        attach(execution,secondMedia,fixture.first);
        assertEquals(3,fixture.rows("mission_checklist_evidence"));
        assertThrows(DataIntegrityViolationException.class,()->fixture.jdbc.update("delete from drone_media where id=?",media));
    }

    @Test void concurrentDuplicateInsertHasExactlyOneWinner() throws Exception {
        var outcomes=fixture.race(()->attach(execution,media,fixture.first));
        assertEquals(1,outcomes.stream().filter(DataIntegrityViolationException.class::isInstance).count());
        assertEquals(1,fixture.rows("mission_checklist_evidence"));
    }

    @Test void optimisticVersionAndRequiredIndexesExist() {
        assertEquals(1,fixture.jdbc.update("update mission_checklist_executions set version=version+1 where id=? and version=0",execution));
        assertEquals(0,fixture.jdbc.update("update mission_checklist_executions set version=version+1 where id=? and version=0",execution));
        fixture.verifyCurrentSchema();
    }
}
