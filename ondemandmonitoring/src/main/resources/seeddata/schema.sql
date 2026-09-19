CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE IF EXISTS public.zones
    ADD COLUMN IF NOT EXISTS restricted BOOLEAN NOT NULL DEFAULT FALSE;

-- Persistent preflight execution history. JPA creates these tables in normal
-- environments; keep the SQL here for deployments that apply seed schema SQL.
CREATE TABLE IF NOT EXISTS public.preflight_checks (
    id VARCHAR(255) PRIMARY KEY,
    mission_id VARCHAR(255) NOT NULL REFERENCES public.missions(id),
    flight_connection_id VARCHAR(255) REFERENCES public.gcs_sessions(id),
    status VARCHAR(20) NOT NULL,
    total_checks INTEGER NOT NULL,
    passed_checks INTEGER NOT NULL DEFAULT 0,
    failed_checks INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

-- Older local databases may already have a legacy preflight_checks table with
-- telemetry columns. Add the execution-history columns without dropping data.
ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS mission_id VARCHAR(255);

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS flight_connection_id VARCHAR(255);

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS total_checks INTEGER;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS passed_checks INTEGER DEFAULT 0;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS failed_checks INTEGER DEFAULT 0;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_preflight_mission_created
    ON public.preflight_checks (mission_id, created_at);

CREATE TABLE IF NOT EXISTS public.preflight_check_items (
    id VARCHAR(255) PRIMARY KEY,
    preflight_check_id VARCHAR(255) NOT NULL REFERENCES public.preflight_checks(id) ON DELETE CASCADE,
    check_type VARCHAR(50) NOT NULL,
    check_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    check_level VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    checked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_preflight_item_type UNIQUE (preflight_check_id, check_type)
);

CREATE INDEX IF NOT EXISTS idx_preflight_item_run
    ON public.preflight_check_items (preflight_check_id);
