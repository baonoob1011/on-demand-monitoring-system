-- Manual PostgreSQL migration, after Hibernate has created mission_device_assignments.
-- There is no battery_snapshot table: planning reads the current MAIN device assignment.
-- SUPPORT assignments are intentionally not singular and must remain current.
-- No history is deleted or automatically reclassified by this migration.
BEGIN;
LOCK TABLE public.mission_device_assignments IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.mission_device_assignments
        WHERE is_current = true AND device_role = 'MAIN'
        GROUP BY mission_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Current MAIN assignment invariant violated: review duplicate MAIN rows and retain the authoritative current assignment before retrying';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mission_device_assignment_current_main
    ON public.mission_device_assignments (mission_id)
    WHERE is_current = true AND device_role = 'MAIN';
COMMIT;
