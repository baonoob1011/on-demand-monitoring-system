-- Repair databases where Hibernate retained FKs from the legacy device_images
-- mapping alongside the current drone_media mapping. No media rows are deleted.
BEGIN;

LOCK TABLE public.media_upload_attempts, public.manual_upload_tasks
    IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    legacy_fk RECORD;
    child_table REGCLASS;
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.media_upload_attempts attempt
        WHERE NOT EXISTS (
            SELECT 1 FROM public.drone_media media WHERE media.id = attempt.media_id
        )
    ) OR EXISTS (
        SELECT 1 FROM public.manual_upload_tasks task
        WHERE NOT EXISTS (
            SELECT 1 FROM public.drone_media media WHERE media.id = task.media_id
        )
    ) THEN
        RAISE EXCEPTION
            'Existing upload rows reference media outside drone_media; migrate those rows before changing FKs';
    END IF;

    FOREACH child_table IN ARRAY ARRAY[
        'public.media_upload_attempts'::REGCLASS,
        'public.manual_upload_tasks'::REGCLASS
    ] LOOP
        IF EXISTS (
            SELECT 1
            FROM pg_catalog.pg_constraint constraint_info
            WHERE constraint_info.conrelid = child_table
              AND constraint_info.contype = 'f'
              AND constraint_info.confrelid = 'public.drone_media'::REGCLASS
        ) THEN
            CONTINUE;
        END IF;

        EXECUTE format(
            'ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (media_id) REFERENCES public.drone_media(id)',
            child_table,
            child_table::TEXT || '_drone_media_fk'
        );
    END LOOP;

    FOR legacy_fk IN
        SELECT constraint_info.conrelid::REGCLASS AS table_name,
               constraint_info.conname AS constraint_name
        FROM pg_catalog.pg_constraint constraint_info
        WHERE constraint_info.contype = 'f'
          AND constraint_info.confrelid = to_regclass('public.device_images')
          AND constraint_info.conrelid IN (
              'public.media_upload_attempts'::REGCLASS,
              'public.manual_upload_tasks'::REGCLASS
          )
    LOOP
        EXECUTE format(
            'ALTER TABLE %s DROP CONSTRAINT %I',
            legacy_fk.table_name,
            legacy_fk.constraint_name
        );
    END LOOP;
END $$;

COMMIT;
