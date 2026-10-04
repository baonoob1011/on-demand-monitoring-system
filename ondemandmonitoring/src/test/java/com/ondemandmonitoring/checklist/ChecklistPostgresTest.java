package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.checklist.domain.*;
import com.ondemandmonitoring.checklist.dto.request.*;
import com.ondemandmonitoring.checklist.mapper.*;
import com.ondemandmonitoring.checklist.repository.*;
import com.ondemandmonitoring.checklist.service.*;
import com.ondemandmonitoring.checklist.service.impl.*;
import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: Hibernate creates tables only in a random schema of a dedicated local test database. */
@SpringJUnitConfig(ChecklistPostgresTest.Config.class)
@EnabledIfEnvironmentVariable(named = "ODMS_CHECKLIST_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/odms_checklist_verification")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChecklistPostgresTest {
    static final String SCHEMA = "checklist_verify_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired IChecklistService catalog;
    @Autowired IServiceChecklistService assignments;
    @Autowired ServiceRepository services;
    @Autowired ServiceChecklistRepository links;
    @Autowired ChecklistDefinitionRepository definitions;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory entityManagerFactory;
    TransactionTemplate transaction;

    @BeforeEach void prepare() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            links.deleteAllInBatch(); definitions.deleteAllInBatch(); services.deleteAllInBatch();
        });
    }

    @AfterAll void cleanup() {
        if (!SCHEMA.matches("checklist_verify_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema");
        new JdbcTemplate(dataSource).execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    String service(String name) {
        return transaction.execute(status -> services.saveAndFlush(
                Service.builder().name(name).isActive(true).build()).getId());
    }

    String checklist(String content) {
        var request = new ChecklistRequest(); request.setContent(content);
        return catalog.create(request).getId();
    }

    ChecklistAssignmentRequest assignment(String id, int order) {
        var request = new ChecklistAssignmentRequest(); request.setChecklistId(id); request.setDisplayOrder(order);
        return request;
    }

    @Test void hibernateSchemaAndCatalogQueriesWork() {
        String id = checklist("  Kiểm tra  lối thoát ");
        assertEquals("Kiểm tra lối thoát", catalog.getById(id).getContent());
        assertEquals(1, catalog.getAll("LỐI", true, PageRequest.of(0, 20)).getTotalItems());
        assertEquals(1, catalog.getAll(null, null, PageRequest.of(0, 20)).getTotalItems());
        assertEquals(0, catalog.getAll("%", null, PageRequest.of(0, 20)).getTotalItems());
        var request = new ChecklistRequest(); request.setContent("New requirement");
        assertEquals("New requirement", catalog.update(id, request).getContent());
    }

    @Test void manyToManyOrderingAndUnassignAreIndependent() {
        String s1 = service("A"), s2 = service("B"), c1 = checklist("One"), c2 = checklist("Two");
        assignments.assign(s1, assignment(c1, 3));
        assignments.assign(s2, assignment(c1, 0));
        assignments.assign(s1, assignment(c2, 1));
        assertEquals(List.of(c2, c1), assignments.getByService(s1, true).stream().map(r -> r.getChecklistId()).toList());
        assertEquals(2, assignments.getByChecklist(c1).size());
        assignments.unassign(s1, c1);
        assertEquals(1, assignments.getByChecklist(c1).size());
        assertNotNull(catalog.getById(c1));
        assertEquals(1, assignments.getByService(s2, true).size());
    }

    @Test void lifecycleKeepsLinksAndReactivationRestoresTemplate() {
        String s = service("A"), c = checklist("One");
        assignments.assign(s, assignment(c, 0));
        catalog.updateStatus(c, false);
        assertTrue(assignments.getByService(s, true).isEmpty());
        assertEquals(1, assignments.getByService(s, false).size());
        catalog.updateStatus(c, true);
        assertEquals(1, assignments.getByService(s, true).size());
        transaction.executeWithoutResult(status -> {
            var row = services.findByIdForUpdate(s).orElseThrow(); row.setIsActive(false); services.saveAndFlush(row);
        });
        assertThrows(ApiException.class, () -> assignments.getByService(s, true));
        assertEquals(1, assignments.getByService(s, false).size());
        assertEquals(1, links.count());
    }

    @Test void databaseRejectsDuplicatePairAndInvalidForeignKey() {
        String s = service("A"), c = checklist("One");
        assignments.assign(s, assignment(c, 0));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String sql = "INSERT INTO service_checklists(id, service_id, checklist_id, display_order, created_at, version) VALUES (?, ?, ?, 0, now(), 0)";
        var duplicate = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(sql, UUID.randomUUID().toString(), s, c));
        assertTrue(duplicate.getMostSpecificCause().getMessage().contains("uk_service_checklist"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(sql, UUID.randomUUID().toString(), s, "missing"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM checklist_definitions WHERE id = ?", c));
        assertEquals(1, links.count());
    }

    @Test void concurrentAssignmentsProduceExactlyOneRelationship() throws Exception {
        String s = service("A"), c = checklist("One");
        List<Object> results = race(() -> assignments.assign(s, assignment(c, 0)));
        assertEquals(1, results.stream().filter(r -> !(r instanceof Throwable)).count());
        assertEquals(1, results.stream().filter(r -> r instanceof ApiException exception
                && exception.getErrorCode() == ErrorCode.SERVICE_CHECKLIST_ALREADY_EXISTS).count());
        assertEquals(1, links.count());
    }

    @Test void concurrentCatalogCreateIsProtectedByDatabaseUniqueConstraint() throws Exception {
        List<Object> results = race(() -> checklist("Same content"));
        assertEquals(1, results.stream().filter(r -> !(r instanceof Throwable)).count());
        assertEquals(1, results.stream().filter(r -> r instanceof DataIntegrityViolationException
                || r instanceof ApiException exception && exception.getErrorCode() == ErrorCode.CHECKLIST_ALREADY_EXISTS).count());
        assertEquals(1, definitions.count());
    }

    @Test void unicodeLowercaseExpansionFitsNormalizedColumn() {
        String id = checklist("\u0130".repeat(500));
        assertEquals(500, catalog.getById(id).getContent().length());
    }

    @Test void databaseRejectsNegativeOrderAndQueryAvoidsNPlusOne() {
        String s = service("A");
        for (int i = 0; i < 8; i++) assignments.assign(s, assignment(checklist("Item " + i), i));
        var jdbc = new JdbcTemplate(dataSource);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE service_checklists SET display_order = -1 WHERE service_id = ?", s));
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();
        assertEquals(8, assignments.getByService(s, true).size());
        assertEquals(2, statistics.getPrepareStatementCount());
    }

    @Test void assignmentWaitsForChecklistDeactivationAndThenRejectsIt() throws Exception {
        String s = service("A"), c = checklist("One");
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> deactivation = pool.submit(() -> transaction.executeWithoutResult(status -> {
                var row = definitions.findByIdForUpdate(c).orElseThrow();
                row.setIsActive(false); definitions.saveAndFlush(row); locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test lock timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException(exception);
                }
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<?> assign = pool.submit(() -> assignments.assign(s, assignment(c, 0)));
            release.countDown(); deactivation.get(10, TimeUnit.SECONDS);
            var failure = assertThrows(ExecutionException.class, () -> assign.get(10, TimeUnit.SECONDS));
            assertEquals(ErrorCode.CHECKLIST_INACTIVE, ((ApiException) failure.getCause()).getErrorCode());
            assertEquals(0, links.count());
        } finally {
            release.countDown();
        }
    }

    private List<Object> race(Callable<?> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Object> call = () -> {
                ready.countDown(); start.await();
                try { return action.call(); } catch (RuntimeException exception) { return exception; }
            };
            Future<Object> first = pool.submit(call), second = pool.submit(call);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = ChecklistDefinitionRepository.class)
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getenv("ODMS_CHECKLIST_TEST_JDBC_URL");
            String user = System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_USER", "checklist_test");
            String password = System.getenv().getOrDefault("ODMS_CHECKLIST_TEST_DB_PASSWORD", "checklist_test_only");
            var base = new DriverManagerDataSource(url, user, password);
            new JdbcTemplate(base).execute("CREATE SCHEMA " + SCHEMA);
            return new DriverManagerDataSource(url + "?currentSchema=" + SCHEMA + ",public", user, password);
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.ondemandmonitoring");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create",
                    "hibernate.generate_statistics", "true",
                    "hibernate.default_schema", SCHEMA, "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean ServiceRepository services(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
                    .getRepository(ServiceRepository.class);
        }
        @Bean IChecklistService catalog(ChecklistDefinitionRepository repository) {
            return new ChecklistServiceImpl(repository, Mappers.getMapper(ChecklistMapper.class));
        }
        @Bean IServiceChecklistService assignments(ServiceRepository services,
                ChecklistDefinitionRepository checklists, ServiceChecklistRepository links) {
            return new ServiceChecklistServiceImpl(services, checklists, links,
                    Mappers.getMapper(ServiceChecklistMapper.class));
        }
    }
}
