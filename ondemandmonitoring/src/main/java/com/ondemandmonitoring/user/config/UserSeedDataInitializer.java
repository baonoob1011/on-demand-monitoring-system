package com.ondemandmonitoring.user.config;

import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.role.repository.RoleRepository;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
@RequiredArgsConstructor
public class UserSeedDataInitializer implements ApplicationRunner {

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Seed Customer",
                    RoleCode.CUSTOMER,
                    "seed.customer@odms.local",
                    UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    "39ea75ec-c001-7087-152e-e76ebbf4740b"),
            new SeedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Seed Staff",
                    RoleCode.STAFF,
                    "seed.staff@odms.local",
                    UUID.fromString("20000000-0000-0000-0000-000000000002"),
                    "e9eab58c-00a1-705d-69b7-c74a17074053"),
            new SeedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    "Seed Drone Operator",
                    RoleCode.DRONE_OPERATOR,
                    "seed.drone.operator@odms.local",
                    UUID.fromString("20000000-0000-0000-0000-000000000003"),
                    "c9cab55c-0081-705a-a1e0-4358cd45d47e"),
            new SeedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000004"),
                    "Seed System Operator",
                    RoleCode.SYSTEM_OPERATOR,
                    "seed.system.operator@odms.local",
                    UUID.fromString("20000000-0000-0000-0000-000000000004"),
                    "792ab50c-9061-7069-6f70-b6729473926c"),
            new SeedUser(
                    UUID.fromString("00000000-0000-0000-0000-000000000005"),
                    "Seed Admin",
                    RoleCode.ADMIN,
                    "seed.admin@odms.local",
                    UUID.fromString("20000000-0000-0000-0000-000000000005"),
                    "e9fa959c-3041-70ef-b624-595c74207d06"));

    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean roleColumnExists = userRoleColumnExists();

        for (SeedUser seed : SEED_USERS) {
            Role role = roleRepository.findByCode(seed.role())
                    .orElseThrow(() -> new IllegalStateException("Missing seed role: " + seed.role()));

            upsertUser(seed, role, roleColumnExists);
            insertIdentity(seed);
        }
    }

    private boolean userRoleColumnExists() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'users'
                  AND column_name = 'role'
                """, Integer.class);
        return count != null && count > 0;
    }

    private void upsertUser(SeedUser seed, Role role, boolean roleColumnExists) {
        if (roleColumnExists) {
            jdbcTemplate.update("""
                    INSERT INTO users (
                        user_id,
                        full_name,
                        role,
                        role_id,
                        email,
                        email_verified,
                        is_active,
                        created_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (email) DO UPDATE SET
                        full_name = EXCLUDED.full_name,
                        role = EXCLUDED.role,
                        role_id = EXCLUDED.role_id,
                        email_verified = EXCLUDED.email_verified,
                        is_active = EXCLUDED.is_active,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    seed.userId(),
                    seed.fullName(),
                    seed.role().name(),
                    role.getId(),
                    seed.email());
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO users (
                    user_id,
                    full_name,
                    role_id,
                    email,
                    email_verified,
                    is_active,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (email) DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    role_id = EXCLUDED.role_id,
                    email_verified = EXCLUDED.email_verified,
                    is_active = EXCLUDED.is_active,
                    updated_at = CURRENT_TIMESTAMP
                """,
                seed.userId(),
                seed.fullName(),
                role.getId(),
                seed.email());
    }

    private void insertIdentity(SeedUser seed) {
        jdbcTemplate.update("""
                INSERT INTO user_identities (
                    identity_id,
                    user_id,
                    provider,
                    cognito_username,
                    cognito_sub,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (provider, cognito_sub) DO NOTHING
                """,
                seed.identityId(),
                seed.userId(),
                IdentityProvider.LOCAL.name(),
                seed.cognitoSub(),
                seed.cognitoSub());
    }

    private record SeedUser(
            UUID userId,
            String fullName,
            RoleCode role,
            String email,
            UUID identityId,
            String cognitoSub) {
    }
}
