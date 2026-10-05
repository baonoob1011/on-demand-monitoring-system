-- Run manually against the target database before deploying the mapping change.
-- No rows are deleted or reassigned. NULL or duplicate orders abort the entire migration.
BEGIN;
LOCK TABLE public.missions IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.missions WHERE order_id IS NULL) THEN
        RAISE EXCEPTION 'Missions without an Order: resolve explicitly before enforcing mandatory Order';
    END IF;
    IF EXISTS (
        SELECT order_id FROM public.missions WHERE order_id IS NOT NULL
        GROUP BY order_id HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate missions per Order: resolve explicitly before adding uk_missions_order';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.missions'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[(SELECT attnum FROM pg_attribute
                             WHERE attrelid = 'public.missions'::regclass AND attname = 'order_id')]
    ) THEN
        ALTER TABLE public.missions ADD CONSTRAINT uk_missions_order UNIQUE (order_id);
    END IF;
    ALTER TABLE public.missions ALTER COLUMN order_id SET NOT NULL;
END $$;
COMMIT;
