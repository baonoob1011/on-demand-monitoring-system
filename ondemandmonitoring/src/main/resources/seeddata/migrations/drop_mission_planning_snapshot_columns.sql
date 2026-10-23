-- Run once on an existing database before switching Hibernate to validate.
BEGIN;

DROP INDEX IF EXISTS public.uk_mission_creation_command;
ALTER TABLE public.missions DROP CONSTRAINT IF EXISTS ck_mission_foundation;
ALTER TABLE public.missions
    DROP COLUMN IF EXISTS target_point,
    DROP COLUMN IF EXISTS target_area,
    DROP COLUMN IF EXISTS finalized_plan_revision,
    DROP COLUMN IF EXISTS creation_idempotency_key,
    DROP COLUMN IF EXISTS creation_request_hash;
ALTER TABLE public.missions
    ADD CONSTRAINT ck_mission_foundation
    CHECK ((replaces_mission_id IS NULL OR replaces_mission_id <> id)
       AND (cancelled_by IS NULL OR cancellation_at IS NOT NULL));

COMMIT;
