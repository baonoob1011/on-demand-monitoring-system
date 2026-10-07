-- Phase 6C. Manual deployment migration; use only isolated PostgreSQL during verification.
-- Half-open [start_time,end_time). Release preserves historical lock rows.
BEGIN;
-- btree_gist 1.7 availability is verified on the isolated PostgreSQL fixture.
-- Deployment requires permission to install this trusted extension when not already installed.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
LOCK TABLE public.resource_time_locks IN ACCESS EXCLUSIVE MODE;
ALTER TABLE public.resource_time_locks ADD COLUMN IF NOT EXISTS resource_type varchar(20);
ALTER TABLE public.resource_time_locks ADD COLUMN IF NOT EXISTS released_at timestamptz;
ALTER TABLE public.resource_time_locks ADD COLUMN IF NOT EXISTS release_reason varchar(500);
ALTER TABLE public.resource_time_locks ADD COLUMN IF NOT EXISTS reserved_by varchar(255);
ALTER TABLE public.resource_time_locks ADD COLUMN IF NOT EXISTS released_by varchar(255);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM public.resource_time_locks WHERE start_time>=end_time) THEN
        RAISE EXCEPTION 'Resource migration refused: invalid reservation windows';
    END IF;
    IF EXISTS (SELECT 1 FROM public.resource_time_locks a JOIN public.resource_time_locks b
        ON a.resource_id=b.resource_id AND a.id<b.id AND a.released_at IS NULL AND b.released_at IS NULL
        AND a.start_time<b.end_time AND a.end_time>b.start_time) THEN
        RAISE EXCEPTION 'Resource migration refused: resolve conflicting reservations with audited release first';
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS public.resource_reservation_mutex(resource_id varchar(100) PRIMARY KEY);
INSERT INTO public.resource_reservation_mutex(resource_id)
    SELECT DISTINCT resource_id FROM public.resource_time_locks ON CONFLICT DO NOTHING;
ALTER TABLE public.resource_time_locks DROP CONSTRAINT IF EXISTS uk_resource_time_locks_resource_mission;
DROP INDEX IF EXISTS public.uk_resource_time_locks_resource_mission;
CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_reservation_active_pair
    ON public.resource_time_locks(resource_id,mission_id) WHERE released_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_resource_reservation_active_window
    ON public.resource_time_locks(resource_id,start_time,end_time) WHERE released_at IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.resource_time_locks'::regclass
        AND conname='ex_resource_reservation_window') THEN
        ALTER TABLE public.resource_time_locks ADD CONSTRAINT ex_resource_reservation_window
            EXCLUDE USING gist(resource_id WITH =, tstzrange(start_time,end_time,'[)') WITH &&)
            WHERE (released_at IS NULL);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.resource_time_locks'::regclass
        AND conname='ck_resource_reservation_window_valid') THEN
        ALTER TABLE public.resource_time_locks ADD CONSTRAINT ck_resource_reservation_window_valid CHECK(start_time<end_time);
    END IF;
END $$;
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_status varchar(40);
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_requested_at timestamptz;
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_requested_by varchar(255);
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_reason varchar(1000);
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_requires_landing boolean;
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_confirmed_at timestamptz;
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_confirmed_by varchar(255);
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_confirmation varchar(1000);
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_cleaned_at timestamptz;
ALTER TABLE public.missions ADD COLUMN IF NOT EXISTS safe_stop_failure_reason varchar(1000);
COMMIT;
