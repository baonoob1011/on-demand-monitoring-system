-- Existing deployments only: align failure-ledger identifiers with Java String mapping.
-- Stop application writers and take a database backup before applying this migration.
-- Run with psql -v ON_ERROR_STOP=1 before starting the application without the converter.
-- Historical add_media_upload_failure_events.sql remains unchanged; apply this after it.
BEGIN;
SET LOCAL search_path = public, pg_catalog;
DO $$
DECLARE
    column_name text;
    actual_type text;
BEGIN
    IF to_regclass('public.media_upload_failure_events') IS NULL THEN
        RAISE EXCEPTION 'media_upload_failure_events missing; provision the schema first';
    END IF;
    LOCK TABLE public.media_upload_failure_events IN ACCESS EXCLUSIVE MODE;
    FOREACH column_name IN ARRAY ARRAY['id','mission_id','media_id','attempt_id'] LOOP
        SELECT format_type(a.atttypid,a.atttypmod) INTO actual_type
        FROM pg_attribute a
        WHERE a.attrelid='public.media_upload_failure_events'::regclass
          AND a.attname=column_name AND NOT a.attisdropped;
        IF actual_type IS NULL OR actual_type NOT IN ('uuid','character varying(255)') THEN
            RAISE EXCEPTION 'Unexpected type for media_upload_failure_events.%: %', column_name, actual_type;
        END IF;
        IF actual_type='uuid' THEN
            -- PostgreSQL retains constraints/indexes; incompatible external dependencies
            -- fail the transaction instead of being dropped or silently recreated.
            EXECUTE format('ALTER TABLE public.media_upload_failure_events ALTER COLUMN %I TYPE varchar(255) USING %I::text',
                           column_name,column_name);
        END IF;
    END LOOP;
END $$;
COMMIT;
