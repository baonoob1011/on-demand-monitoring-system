-- Run before RoleDataInitializer; also safe to execute manually while the application is stopped.
-- Preserves user IDs and mission assignments. The marker prevents mapping the new STAFF twice.
CREATE TABLE IF NOT EXISTS role_model_migrations (
    name VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $migration$
DECLARE
    legacy_staff UUID;
    manager_role UUID;
    staff_role UUID;
    constraint_name TEXT;
BEGIN
    LOCK TABLE roles, users IN SHARE ROW EXCLUSIVE MODE;
    IF EXISTS (SELECT 1 FROM role_model_migrations WHERE name = 'four_system_roles_v1') THEN
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM roles WHERE code IN ('DRONE_OPERATOR', 'SYSTEM_OPERATOR')) THEN
        INSERT INTO role_model_migrations(name) VALUES ('four_system_roles_v1');
        RETURN;
    END IF;

    -- Hibernate may have generated a check constraint for the previous enum.
    FOR constraint_name IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'roles'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) ~ '\mcode\M'
    LOOP
        EXECUTE format('ALTER TABLE roles DROP CONSTRAINT %I', constraint_name);
    END LOOP;

    CREATE TABLE IF NOT EXISTS role_model_v1_user_backup AS
        SELECT u.id AS user_id, u.role_id, r.code AS old_role_code
        FROM users u JOIN roles r ON r.role_id = u.role_id;

    SELECT role_id INTO legacy_staff FROM roles WHERE code = 'STAFF';
    SELECT role_id INTO manager_role FROM roles WHERE code = 'MANAGER';
    IF manager_role IS NULL AND legacy_staff IS NOT NULL THEN
        UPDATE roles SET code = 'MANAGER', name = 'MANAGER',
            description = 'MISSION COORDINATION ACCOUNT', updated_at = CURRENT_TIMESTAMP
        WHERE role_id = legacy_staff;
    ELSIF legacy_staff IS NOT NULL THEN
        UPDATE users SET role_id = manager_role WHERE role_id = legacy_staff;
        DELETE FROM roles WHERE role_id = legacy_staff;
    END IF;

    SELECT role_id INTO staff_role FROM roles WHERE code = 'DRONE_OPERATOR';
    IF staff_role IS NULL THEN
        SELECT role_id INTO staff_role FROM roles WHERE code = 'SYSTEM_OPERATOR';
    END IF;
    UPDATE roles SET code = 'STAFF', name = 'STAFF', description = 'ASSIGNED OPERATIONS ACCOUNT',
        updated_at = CURRENT_TIMESTAMP WHERE role_id = staff_role;
    UPDATE users SET role_id = staff_role
        WHERE role_id IN (SELECT role_id FROM roles WHERE code IN ('DRONE_OPERATOR', 'SYSTEM_OPERATOR'));
    DELETE FROM roles WHERE code IN ('DRONE_OPERATOR', 'SYSTEM_OPERATOR');
    -- Older databases retain the denormalized users.role column until schema cleanup.
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'role') THEN
        FOR constraint_name IN
            SELECT conname FROM pg_constraint
            WHERE conrelid = 'users'::regclass AND contype = 'c'
              AND pg_get_constraintdef(oid) ~ '\mrole\M'
        LOOP
            EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', constraint_name);
        END LOOP;
        EXECUTE 'UPDATE users u SET role = r.code FROM roles r WHERE r.role_id = u.role_id';
    END IF;
    ALTER TABLE roles ADD CONSTRAINT ck_roles_four_system_codes
        CHECK (code IN ('ADMIN', 'MANAGER', 'STAFF', 'CUSTOMER'));
    INSERT INTO role_model_migrations(name) VALUES ('four_system_roles_v1');
END;
$migration$;
