
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
VALUES
    ('00000000-0000-0000-0000-000000000001', 'Seed Customer', (SELECT role_id FROM roles WHERE code = 'CUSTOMER'),
     'seed.customer@odms.local', true, true,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'Seed Staff', (SELECT role_id FROM roles WHERE code = 'STAFF'),
     'seed.staff@odms.local', true, true,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'Seed Drone Operator', (SELECT role_id FROM roles WHERE code = 'DRONE_OPERATOR'),
     'seed.drone.operator@odms.local', true, true,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000004', 'Seed System Operator', (SELECT role_id FROM roles WHERE code = 'SYSTEM_OPERATOR'),
     'seed.system.operator@odms.local', true, true,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000005', 'Seed Admin', (SELECT role_id FROM roles WHERE code = 'ADMIN'),
     'seed.admin@odms.local', true, true,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    role_id = EXCLUDED.role_id,
    email_verified = EXCLUDED.email_verified,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_identities (
    identity_id,
    user_id,
    provider,
    cognito_username,
    cognito_sub,
    created_at,
    updated_at
)
VALUES
    ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'LOCAL', '39ea75ec-c001-7087-152e-e76ebbf4740b', '39ea75ec-c001-7087-152e-e76ebbf4740b', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'LOCAL', 'e9eab58c-00a1-705d-69b7-c74a17074053', 'e9eab58c-00a1-705d-69b7-c74a17074053', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', 'LOCAL', 'c9cab55c-0081-705a-a1e0-4358cd45d47e', 'c9cab55c-0081-705a-a1e0-4358cd45d47e', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000004', 'LOCAL', '792ab50c-9061-7069-6f70-b6729473926c', '792ab50c-9061-7069-6f70-b6729473926c', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000005', 'LOCAL', 'e9fa959c-3041-70ef-b624-595c74207d06', 'e9fa959c-3041-70ef-b624-595c74207d06', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (provider, cognito_sub) DO NOTHING;

------------------------------------------------------------------------------------------------------------------------

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower
    ON users (LOWER(email));

ALTER TABLE user_identities
DROP CONSTRAINT IF EXISTS uk_user_identity_cognito_sub;

ALTER TABLE user_identities
DROP CONSTRAINT IF EXISTS user_identities_cognito_sub_key;

ALTER TABLE user_identities
DROP CONSTRAINT IF EXISTS uk_user_identity_cognito_username;

ALTER TABLE user_identities
DROP CONSTRAINT IF EXISTS user_identities_cognito_username_key;

DROP INDEX IF EXISTS user_identities_cognito_username_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_identity_provider_sub
    ON user_identities (provider, cognito_sub);
