package com.ondemandmonitoring.role;

import com.ondemandmonitoring.role.infrastructure.RoleModelMigration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Opt-in integration tests: always use a disposable role_verification database. */
@EnabledIfEnvironmentVariable(named = "ODMS_ROLE_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/role_verification")
class RoleMigrationPostgresTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private String schema;

    @BeforeEach
    void setup() {
        String url = System.getenv("ODMS_ROLE_TEST_JDBC_URL");
        var base = new DriverManagerDataSource(url, "role_test", "role_test_only");
        admin = new JdbcTemplate(base);
        schema = "role_verify_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema);
        var isolated = new DriverManagerDataSource(url.replace("/role_verification", "/" + schema),
                "role_test", "role_test_only");
        jdbc = new JdbcTemplate(isolated);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(isolated));
        jdbc.execute("""
                CREATE TABLE roles (
                    role_id UUID PRIMARY KEY, code VARCHAR(30) UNIQUE NOT NULL,
                    name VARCHAR(100) NOT NULL, description VARCHAR(255),
                    is_active BOOLEAN NOT NULL DEFAULT true, is_system_role BOOLEAN NOT NULL DEFAULT true,
                    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT legacy_role_codes CHECK(code IN
                        ('ADMIN', 'CUSTOMER', 'STAFF', 'DRONE_OPERATOR', 'SYSTEM_OPERATOR'))
                );
                CREATE TABLE users (
                    id VARCHAR(255) PRIMARY KEY, email VARCHAR(150) UNIQUE NOT NULL,
                    role_id UUID NOT NULL REFERENCES roles(role_id), version BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
                );
                CREATE TABLE user_identities (
                    id VARCHAR(255) PRIMARY KEY, user_id VARCHAR(255) NOT NULL REFERENCES users(id),
                    provider VARCHAR(20), cognito_sub VARCHAR(64), cognito_username VARCHAR(128)
                );
                CREATE TABLE mission_staff_assignments (
                    id VARCHAR(255) PRIMARY KEY, staff_id VARCHAR(255) NOT NULL REFERENCES users(id)
                );
                """);
    }

    @AfterEach
    void cleanup() {
        if (schema != null && schema.matches("role_verify_[a-f0-9]{32}")) {
            admin.execute("DROP DATABASE " + schema);
        }
    }

    @Test
    void migratesLegacyRolesAndPreservesIdentitiesAssignmentsAndRoleIdsOnRestart() {
        seedLegacy();
        migrate();
        assertMappings();
        assertThat(jdbc.queryForList("SELECT code FROM roles ORDER BY code", String.class))
                .containsExactly("ADMIN", "CUSTOMER", "MANAGER", "STAFF");
        assertThat(roleId("MANAGER")).isEqualTo("10000000-0000-0000-0000-000000000003");
        assertThat(roleId("STAFF")).isEqualTo("10000000-0000-0000-0000-000000000004");
        assertThat(count("user_identities")).isEqualTo(5);
        assertThat(count("mission_staff_assignments")).isEqualTo(5);
        assertThat(count("role_model_v1_user_backup")).isEqualTo(5);
        migrate();
        assertMappings();
        assertThat(count("role_model_migrations")).isEqualTo(1);
    }

    @Test
    void updatesLegacyDenormalizedUserRoleColumnAndItsOldConstraint() {
        seedLegacy();
        jdbc.execute("ALTER TABLE users ADD COLUMN role VARCHAR(30)");
        jdbc.execute("UPDATE users u SET role = r.code FROM roles r WHERE u.role_id = r.role_id");
        jdbc.execute("""
                ALTER TABLE users ADD CONSTRAINT legacy_user_role CHECK
                    (role IN ('ADMIN', 'CUSTOMER', 'STAFF', 'DRONE_OPERATOR', 'SYSTEM_OPERATOR'));
                """);
        migrate();
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = 'user-STAFF'", String.class))
                .isEqualTo("MANAGER");
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = 'user-SYSTEM_OPERATOR'", String.class))
                .isEqualTo("STAFF");
        migrate();
        assertMappings();
    }

    @Test
    void mergesOldStaffIntoExistingManagerWithoutDeletingUsers() {
        seedLegacy();
        jdbc.execute("ALTER TABLE roles DROP CONSTRAINT legacy_role_codes");
        jdbc.execute("""
                INSERT INTO roles(role_id, code, name)
                VALUES ('10000000-0000-0000-0000-000000000006', 'MANAGER', 'MANAGER');
                """);
        migrate();
        assertMappings();
        assertThat(roleId("MANAGER")).isEqualTo("10000000-0000-0000-0000-000000000006");
        assertThat(count("users")).isEqualTo(5);
    }

    @Test
    void supportsLegacyPoolWithOnlySystemOperatorAsOperationalRole() {
        seedLegacy();
        jdbc.execute("UPDATE users SET role_id = '10000000-0000-0000-0000-000000000005' WHERE id = 'user-DRONE_OPERATOR'");
        jdbc.execute("DELETE FROM roles WHERE code = 'DRONE_OPERATOR'");
        migrate();
        assertMappings();
        assertThat(roleId("STAFF")).isEqualTo("10000000-0000-0000-0000-000000000005");
    }

    @Test
    void freshModelIsNotReinterpretedAsLegacyStaff() {
        migrate();
        jdbc.execute("""
                ALTER TABLE roles DROP CONSTRAINT legacy_role_codes;
                INSERT INTO roles(role_id, code, name)
                VALUES ('10000000-0000-0000-0000-000000000004', 'STAFF', 'STAFF');
                INSERT INTO users(id, email, role_id)
                VALUES ('new-staff', 'new-staff@example.test', '10000000-0000-0000-0000-000000000004');
                """);
        migrate();
        assertThat(userRole("new-staff")).isEqualTo("STAFF");
        assertThat(count("role_model_migrations")).isEqualTo(1);
    }

    @Test
    void failureRollsBackMigrationAndMarkerTogether() {
        seedLegacy();
        jdbc.execute("""
                CREATE FUNCTION refuse_role_update() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'verification failure'; END; $$;
                CREATE TRIGGER reject_role_update BEFORE UPDATE ON roles
                    FOR EACH ROW EXECUTE FUNCTION refuse_role_update();
                """);
        assertThatThrownBy(this::migrate).isInstanceOf(RuntimeException.class);
        assertThat(count("roles")).isEqualTo(5);
        assertThat(userRole("user-STAFF")).isEqualTo("STAFF");
        assertThat(jdbc.queryForObject("SELECT to_regclass('role_model_migrations') IS NULL", Boolean.class)).isTrue();
    }

    private void seedLegacy() {
        String[] roles = {"ADMIN", "CUSTOMER", "STAFF", "DRONE_OPERATOR", "SYSTEM_OPERATOR"};
        for (int index = 0; index < roles.length; index++) {
            String code = roles[index];
            String roleId = "10000000-0000-0000-0000-" + String.format("%012d", index + 1);
            jdbc.update("INSERT INTO roles(role_id,code,name) VALUES (?::uuid,?,?)", roleId, code, code);
            jdbc.update("INSERT INTO users(id,email,role_id) VALUES (?,?,?::uuid)",
                    "user-" + code, code.toLowerCase() + "@example.test", roleId);
            jdbc.update("INSERT INTO user_identities VALUES (?,?,'LOCAL',?,?)",
                    "identity-" + code, "user-" + code, "sub-" + code, "username-" + code);
            jdbc.update("INSERT INTO mission_staff_assignments VALUES (?,?)", "assignment-" + code, "user-" + code);
        }
    }

    private void migrate() {
        transaction.executeWithoutResult(status -> {
            try {
                new RoleModelMigration(jdbc).run(null);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private void assertMappings() {
        assertThat(userRole("user-ADMIN")).isEqualTo("ADMIN");
        assertThat(userRole("user-CUSTOMER")).isEqualTo("CUSTOMER");
        assertThat(userRole("user-STAFF")).isEqualTo("MANAGER");
        assertThat(userRole("user-DRONE_OPERATOR")).isEqualTo("STAFF");
        assertThat(userRole("user-SYSTEM_OPERATOR")).isEqualTo("STAFF");
    }

    private String userRole(String user) {
        return jdbc.queryForObject("SELECT r.code FROM users u JOIN roles r ON u.role_id = r.role_id WHERE u.id = ?",
                String.class, user);
    }

    private String roleId(String role) {
        return jdbc.queryForObject("SELECT role_id::text FROM roles WHERE code = ?", String.class, role);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
