-- Optional manual seed for the current schema. Start the application first to initialize roles.
-- Cognito sub/username remain unchanged; update the manager email attribute in Cognito separately.
BEGIN;
INSERT INTO users (id, full_name, role_id, email, email_verified, is_active, version, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'Seed Customer 1', (SELECT role_id FROM roles WHERE code = 'CUSTOMER'), 'seed.customer@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000006', 'Seed Customer 2', (SELECT role_id FROM roles WHERE code = 'CUSTOMER'), 'seed.customer.2@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000007', 'Seed Customer 3', (SELECT role_id FROM roles WHERE code = 'CUSTOMER'), 'seed.customer.3@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000008', 'Seed Customer 4', (SELECT role_id FROM roles WHERE code = 'CUSTOMER'), 'seed.customer.4@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'Seed Manager 1', (SELECT role_id FROM roles WHERE code = 'MANAGER'), 'seed.manager@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000009', 'Seed Manager 2', (SELECT role_id FROM roles WHERE code = 'MANAGER'), 'seed.manager.2@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000010', 'Seed Manager 3', (SELECT role_id FROM roles WHERE code = 'MANAGER'), 'seed.manager.3@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000011', 'Seed Manager 4', (SELECT role_id FROM roles WHERE code = 'MANAGER'), 'seed.manager.4@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'Seed Staff 1', (SELECT role_id FROM roles WHERE code = 'STAFF'), 'seed.drone.operator@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000012', 'Seed Staff 2', (SELECT role_id FROM roles WHERE code = 'STAFF'), 'seed.drone.operator.2@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000013', 'Seed Staff 3', (SELECT role_id FROM roles WHERE code = 'STAFF'), 'seed.drone.operator.3@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000014', 'Seed Staff 4', (SELECT role_id FROM roles WHERE code = 'STAFF'), 'seed.drone.operator.4@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000004', 'Seed Staff Technician', (SELECT role_id FROM roles WHERE code = 'STAFF'), 'seed.system.operator@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000005', 'Seed Admin', (SELECT role_id FROM roles WHERE code = 'ADMIN'), 'seed.admin@odms.local', true, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO user_identities (id, user_id, provider, cognito_username, cognito_sub, version, created_at, updated_at)
VALUES
    ('20000000-0000-0000-0000-000000000001', (SELECT id FROM users WHERE email = 'seed.customer@odms.local'), 'LOCAL', '39ea75ec-c001-7087-152e-e76ebbf4740b', '39ea75ec-c001-7087-152e-e76ebbf4740b', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000006', (SELECT id FROM users WHERE email = 'seed.customer.2@odms.local'), 'LOCAL', '79cad52c-90e1-7069-5054-4d5073f30ea0', '79cad52c-90e1-7069-5054-4d5073f30ea0', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000007', (SELECT id FROM users WHERE email = 'seed.customer.3@odms.local'), 'LOCAL', 'b9ba05cc-70c1-707b-37d6-12c83bd919ae', 'b9ba05cc-70c1-707b-37d6-12c83bd919ae', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000008', (SELECT id FROM users WHERE email = 'seed.customer.4@odms.local'), 'LOCAL', '395a554c-9071-7082-89df-f0675ae6a6fe', '395a554c-9071-7082-89df-f0675ae6a6fe', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000002', (SELECT id FROM users WHERE email = 'seed.manager@odms.local'), 'LOCAL', 'e9eab58c-00a1-705d-69b7-c74a17074053', 'e9eab58c-00a1-705d-69b7-c74a17074053', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000009', (SELECT id FROM users WHERE email = 'seed.manager.2@odms.local'), 'LOCAL', 'f97ab50c-9071-70ad-2df1-9009bcea418d', 'f97ab50c-9071-70ad-2df1-9009bcea418d', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000010', (SELECT id FROM users WHERE email = 'seed.manager.3@odms.local'), 'LOCAL', '19dae58c-2091-706d-6499-53583c362c64', '19dae58c-2091-706d-6499-53583c362c64', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000011', (SELECT id FROM users WHERE email = 'seed.manager.4@odms.local'), 'LOCAL', '895a956c-30d1-70ba-6a53-e0c9372084e3', '895a956c-30d1-70ba-6a53-e0c9372084e3', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000003', (SELECT id FROM users WHERE email = 'seed.drone.operator@odms.local'), 'LOCAL', 'c9cab55c-0081-705a-a1e0-4358cd45d47e', 'c9cab55c-0081-705a-a1e0-4358cd45d47e', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000012', (SELECT id FROM users WHERE email = 'seed.drone.operator.2@odms.local'), 'LOCAL', 'b9da65dc-20c1-70b1-2f9e-97de9e75be80', 'b9da65dc-20c1-70b1-2f9e-97de9e75be80', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000013', (SELECT id FROM users WHERE email = 'seed.drone.operator.3@odms.local'), 'LOCAL', '698ac5bc-7081-7030-37eb-4dd912d0f2d8', '698ac5bc-7081-7030-37eb-4dd912d0f2d8', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000014', (SELECT id FROM users WHERE email = 'seed.drone.operator.4@odms.local'), 'LOCAL', 'c9aa556c-e001-70e7-340a-df3b92f2a208', 'c9aa556c-e001-70e7-340a-df3b92f2a208', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000004', (SELECT id FROM users WHERE email = 'seed.system.operator@odms.local'), 'LOCAL', '792ab50c-9061-7069-6f70-b6729473926c', '792ab50c-9061-7069-6f70-b6729473926c', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000005', (SELECT id FROM users WHERE email = 'seed.admin@odms.local'), 'LOCAL', 'e9fa959c-3041-70ef-b624-595c74207d06', 'e9fa959c-3041-70ef-b624-595c74207d06', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
COMMIT;
