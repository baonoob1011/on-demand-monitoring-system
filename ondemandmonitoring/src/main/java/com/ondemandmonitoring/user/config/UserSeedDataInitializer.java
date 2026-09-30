package com.ondemandmonitoring.user.config;

import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.role.repository.RoleRepository;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import com.ondemandmonitoring.user.repository.UserRepository;
import java.util.List;
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
                    "00000000-0000-0000-0000-000000000001",
                    "Seed Customer 1",
                    RoleCode.CUSTOMER,
                    "seed.customer@odms.local",
                    "20000000-0000-0000-0000-000000000001",
                    "39ea75ec-c001-7087-152e-e76ebbf4740b"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000006",
                    "Seed Customer 2",
                    RoleCode.CUSTOMER,
                    "seed.customer.2@odms.local",
                    "20000000-0000-0000-0000-000000000006",
                    "79cad52c-90e1-7069-5054-4d5073f30ea0"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000007",
                    "Seed Customer 3",
                    RoleCode.CUSTOMER,
                    "seed.customer.3@odms.local",
                    "20000000-0000-0000-0000-000000000007",
                    "b9ba05cc-70c1-707b-37d6-12c83bd919ae"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000008",
                    "Seed Customer 4",
                    RoleCode.CUSTOMER,
                    "seed.customer.4@odms.local",
                    "20000000-0000-0000-0000-000000000008",
                    "395a554c-9071-7082-89df-f0675ae6a6fe"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000002",
                    "Seed Staff 1",
                    RoleCode.STAFF,
                    "seed.staff@odms.local",
                    "20000000-0000-0000-0000-000000000002",
                    "e9eab58c-00a1-705d-69b7-c74a17074053"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000009",
                    "Seed Staff 2",
                    RoleCode.STAFF,
                    "seed.staff.2@odms.local",
                    "20000000-0000-0000-0000-000000000009",
                    "f97ab50c-9071-70ad-2df1-9009bcea418d"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000010",
                    "Seed Staff 3",
                    RoleCode.STAFF,
                    "seed.staff.3@odms.local",
                    "20000000-0000-0000-0000-000000000010",
                    "19dae58c-2091-706d-6499-53583c362c64"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000011",
                    "Seed Staff 4",
                    RoleCode.STAFF,
                    "seed.staff.4@odms.local",
                    "20000000-0000-0000-0000-000000000011",
                    "895a956c-30d1-70ba-6a53-e0c9372084e3"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000003",
                    "Seed Drone Operator 1",
                    RoleCode.DRONE_OPERATOR,
                    "seed.drone.operator@odms.local",
                    "20000000-0000-0000-0000-000000000003",
                    "c9cab55c-0081-705a-a1e0-4358cd45d47e"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000012",
                    "Seed Drone Operator 2",
                    RoleCode.DRONE_OPERATOR,
                    "seed.drone.operator.2@odms.local",
                    "20000000-0000-0000-0000-000000000012",
                    "b9da65dc-20c1-70b1-2f9e-97de9e75be80"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000013",
                    "Seed Drone Operator 3",
                    RoleCode.DRONE_OPERATOR,
                    "seed.drone.operator.3@odms.local",
                    "20000000-0000-0000-0000-000000000013",
                    "698ac5bc-7081-7030-37eb-4dd912d0f2d8"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000014",
                    "Seed Drone Operator 4",
                    RoleCode.DRONE_OPERATOR,
                    "seed.drone.operator.4@odms.local",
                    "20000000-0000-0000-0000-000000000014",
                    "c9aa556c-e001-70e7-340a-df3b92f2a208"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000004",
                    "Seed System Operator",
                    RoleCode.SYSTEM_OPERATOR,
                    "seed.system.operator@odms.local",
                    "20000000-0000-0000-0000-000000000004",
                    "792ab50c-9061-7069-6f70-b6729473926c"
            ),
            new SeedUser(
                    "00000000-0000-0000-0000-000000000005",
                    "Seed Admin",
                    RoleCode.ADMIN,
                    "seed.admin@odms.local",
                    "20000000-0000-0000-0000-000000000005",
                    "e9fa959c-3041-70ef-b624-595c74207d06"
            )
    );

    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean roleColumnExists = userRoleColumnExists();

        for (SeedUser seed : SEED_USERS) {
            Role role = roleRepository.findByCode(seed.role())
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Missing seed role: " + seed.role()
                            )
                    );

            upsertUser(seed, role, roleColumnExists);
            insertIdentity(seed);
            provisionCustomerProfile(seed);
        }
    }

    private void provisionCustomerProfile(SeedUser seed) {
        if (seed.role() != RoleCode.CUSTOMER) {
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(seed.email())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Missing seeded customer after upsert: "
                                        + seed.email()
                        )
                );

        if (!customerProfileRepository.existsById(user.getId())) {
            customerProfileRepository.save(
                    CustomerProfile.builder()
                            .user(user)
                            .build()
            );
        }
    }

    private boolean userRoleColumnExists() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'users'
                  AND column_name = 'role'
                """,
                Integer.class
        );

        return count != null && count > 0;
    }

    private void upsertUser(
            SeedUser seed,
            Role role,
            boolean roleColumnExists
    ) {
        if (roleColumnExists) {
            jdbcTemplate.update("""
                    INSERT INTO users (
                        id,
                        full_name,
                        role,
                        role_id,
                        email,
                        email_verified,
                        is_active,
                        version,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        ?, ?, ?, ?, ?,
                        true,
                        true,
                        0,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    )
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
                    seed.email()
            );

            return;
        }

        jdbcTemplate.update("""
                INSERT INTO users (
                    id,
                    full_name,
                    role_id,
                    email,
                    email_verified,
                    is_active,
                    version,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?,
                    true,
                    true,
                    0,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
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
                seed.email()
        );
    }

    private void insertIdentity(SeedUser seed) {
        jdbcTemplate.update("""
            INSERT INTO user_identities (
                id,
                user_id,
                provider,
                cognito_username,
                cognito_sub,
                version,
                created_at,
                updated_at
            )
            VALUES (
                ?, ?, ?, ?, ?,
                0,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (provider, cognito_sub) DO NOTHING
            """,
                seed.identityId(),
                seed.userId(),
                IdentityProvider.LOCAL.name(),
                seed.cognitoSub(),
                seed.cognitoSub()
        );
    }
    private record SeedUser(
            String userId,
            String fullName,
            RoleCode role,
            String email,
            String identityId,
            String cognitoSub
    ) {
    }
}
