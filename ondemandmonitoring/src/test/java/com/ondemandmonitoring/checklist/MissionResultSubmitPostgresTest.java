package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.media.mapper.MediaAssetMapper;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.service.IMediaAssetService;
import com.ondemandmonitoring.mission.dto.request.MissionResultRequest;
import com.ondemandmonitoring.mission.enums.MissionStaffRole;
import com.ondemandmonitoring.mission.mapper.MissionResultMapper;
import com.ondemandmonitoring.mission.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.mission.service.impl.MissionAuthorizationServiceImpl;
import com.ondemandmonitoring.mission.service.impl.MissionResultService;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated seeded completion state, real actor policy/submission/JPA/aggregate; no real-auth E2E claim. */
@SpringJUnitConfig(OrderAggregateCompletionPostgresTest.Beans.class)
class MissionResultSubmitPostgresTest extends OrderAggregateCompletionPostgresTest {
    @Test void operatorSubmissionPersistsIndependentResultsAndPreservesAggregateCompletion() {
        ready(1);
        String firstOperator = account(RoleCode.STAFF);
        String secondOperator = account(RoleCode.STAFF);
        String inspector = account(RoleCode.STAFF);
        String manager = account(RoleCode.MANAGER);
        assignment(first, firstOperator, MissionStaffRole.OPERATOR);
        assignment(second, secondOperator, MissionStaffRole.OPERATOR);
        assignment(first, inspector, MissionStaffRole.INSPECTOR);
        var policy = new MissionAuthorizationServiceImpl(repository(entityManagerFactory, MissionStaffAssignmentRepository.class),
                missions, resolver, resultRepository, scope);
        var mapper = Mappers.getMapper(MissionResultMapper.class);
        ReflectionTestUtils.setField(mapper, "mediaAssetMapper", Mappers.getMapper(MediaAssetMapper.class));
        var submit = new MissionResultService(resultRepository, missions, repository(entityManagerFactory, MediaAssetRepository.class),
                mock(IMediaAssetService.class), mapper, completion, runtime, resolver, policy);

        actor(firstOperator);
        transaction.executeWithoutResult(tx -> {
            assertTrue(policy.canSubmitMissionResult(first));
            assertFalse(policy.canUploadMissionMedia(first));
            assertFalse(policy.canSubmitMissionResult(second));
        });
        String firstResult = transaction.execute(tx -> submit.upsert(first, new MissionResultRequest()).getId());
        var forbiddenSibling = assertThrows(ApiException.class,
                () -> transaction.execute(tx -> submit.upsert(second, new MissionResultRequest())));
        assertEquals(ErrorCode.ACCESS_DENIED, forbiddenSibling.getErrorCode());
        assertEquals(1, rows("mission_results"));

        actor(inspector);
        transaction.executeWithoutResult(tx -> {
            assertTrue(policy.canUploadMissionMedia(first));
            assertFalse(policy.canSubmitMissionResult(first));
        });
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(ApiException.class,
                () -> transaction.execute(tx -> submit.upsert(first, new MissionResultRequest()))).getErrorCode());
        actor(manager); resultService.approve(firstResult, null);
        assertEquals(OrderStatus.APPROVED, status());

        actor(secondOperator);
        String secondResult = transaction.execute(tx -> submit.upsert(second, new MissionResultRequest()).getId());
        assertNotEquals(firstResult, secondResult);
        actor(manager); resultService.approve(secondResult, null);
        assertEquals(OrderStatus.COMPLETED, status());
        assertEquals(2, rows("mission_results"));
    }

    private String account(RoleCode code) {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into roles(role_id,code,name,is_system_role,is_active,created_at,updated_at) values (?::uuid,?,?,false,true,now(),now()) on conflict(code) do nothing",
                UUID.randomUUID().toString(), code.name(), code.name());
        jdbc.update("insert into users(id,created_at,version,full_name,email,email_verified,is_active,role_id) select ?,now(),0,'Result test actor',?,true,true,role_id from roles where code=?",
                id, id + "@test.local", code.name());
        return id;
    }

    private void assignment(String missionId, String userId, MissionStaffRole role) {
        jdbc.update("insert into mission_staff_assignments(id,created_at,version,mission_id,staff_id,assigned_role,response_status,is_current,released_at,release_reason) values (?,now(),0,?,?,?,'ACCEPTED',true,now(),'MISSION_COMPLETE')",
                UUID.randomUUID().toString(), missionId, userId, role.name());
    }

    private void actor(String id) {
        var user = transaction.execute(tx -> repository(entityManagerFactory, UserRepository.class).findById(id).orElseThrow());
        when(resolver.getCurrentUser()).thenReturn(user);
        when(resolver.getCurrentUserId()).thenReturn(id);
    }

    private static <T> T repository(EntityManagerFactory factory, Class<T> type) {
        return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(type);
    }
}
