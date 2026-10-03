-- Only for a disposable role_verification_legacy database cloned from the startup test.
DO $fixture$
DECLARE
    constraint_name TEXT;
BEGIN
    IF current_database() <> 'role_verification_legacy' THEN
        RAISE EXCEPTION 'Refusing to alter a non-verification database';
    END IF;
    FOR constraint_name IN SELECT conname FROM pg_constraint
        WHERE conrelid = 'roles'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) ~ '\mcode\M'
    LOOP
        EXECUTE format('ALTER TABLE roles DROP CONSTRAINT %I', constraint_name);
    END LOOP;
    UPDATE roles SET code = 'DRONE_OPERATOR', name = 'DRONE_OPERATOR' WHERE code = 'STAFF';
    UPDATE roles SET code = 'STAFF', name = 'STAFF' WHERE code = 'MANAGER';
    INSERT INTO roles(role_id, code, name, is_active, is_system_role, created_at, updated_at)
    VALUES ('10000000-0000-0000-0000-000000000005', 'SYSTEM_OPERATOR', 'SYSTEM_OPERATOR',
            true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    UPDATE users SET role_id = '10000000-0000-0000-0000-000000000005'
        WHERE email = 'seed.system.operator@odms.local';
    ALTER TABLE roles ADD CONSTRAINT legacy_role_codes
        CHECK(code IN ('ADMIN', 'CUSTOMER', 'STAFF', 'DRONE_OPERATOR', 'SYSTEM_OPERATOR'));
END;
$fixture$;
