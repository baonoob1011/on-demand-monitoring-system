-- PHASE 4. Apply manually AFTER add_order_multi_mission_foundation.sql, BEFORE new creation code.
-- Planning cardinality only. Never rerun the historical 1:1/foundation migrations after this cutover.
-- New keyed Missions are runtime-blocked by the application; legacy data/keys are not rewritten.
BEGIN;
LOCK TABLE public.orders, public.missions IN ACCESS EXCLUSIVE MODE;
DO $$ DECLARE entry record; order_att smallint; BEGIN
    SELECT attnum INTO order_att FROM pg_attribute
        WHERE attrelid='public.missions'::regclass AND attname='order_id' AND NOT attisdropped;
    IF order_att IS NULL OR NOT EXISTS (SELECT 1 FROM pg_attribute
        WHERE attrelid='public.missions'::regclass AND attname='order_id' AND attnotnull)
        OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.missions'::regclass
            AND contype='f' AND confrelid='public.orders'::regclass AND conkey=ARRAY[order_att])
        OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.missions'::regclass
            AND contype='u' AND pg_get_constraintdef(oid)='UNIQUE (id, order_id)')
        OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.missions'::regclass
            AND conname='ck_mission_foundation') THEN
        RAISE EXCEPTION 'Multi-Mission cutover requires verified Phase 2 foundation, NOT NULL, FK and composite identity';
    END IF;
    -- Constraint names differ across historical/manual/Hibernate databases. No CASCADE.
    FOR entry IN SELECT conname FROM pg_constraint WHERE conrelid='public.missions'::regclass
        AND contype='u' AND conkey=ARRAY[order_att] LOOP
        EXECUTE format('ALTER TABLE public.missions DROP CONSTRAINT %I', entry.conname);
    END LOOP;
    -- Also cover standalone UNIQUE(order_id) indexes (including INCLUDE columns).
    FOR entry IN SELECT index_rel.relname AS index_name FROM pg_index idx
        JOIN pg_class index_rel ON index_rel.oid=idx.indexrelid
        WHERE idx.indrelid='public.missions'::regclass AND idx.indisunique
          AND idx.indnkeyatts=1 AND idx.indkey[0]=order_att
          AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conindid=idx.indexrelid) LOOP
        EXECUTE format('DROP INDEX public.%I', entry.index_name);
    END LOOP;
END $$;
CREATE INDEX IF NOT EXISTS ix_mission_order_schedule ON public.missions(order_id,scheduled_start_at,id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_mission_creation_command
    ON public.missions(order_id,creation_idempotency_key) WHERE creation_idempotency_key IS NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_index WHERE indexrelid='public.uk_mission_creation_command'::regclass
        AND indisunique AND indnkeyatts=2
        AND pg_get_indexdef(indexrelid) LIKE 'CREATE UNIQUE INDEX %(order_id, creation_idempotency_key)%'
        AND pg_get_expr(indpred, indrelid)='(creation_idempotency_key IS NOT NULL)') THEN
        RAISE EXCEPTION 'Unexpected idempotency index definition; review schema before cutover';
    END IF;
END $$;
-- Global UNIQUE(order_checklist_item_id), evidence identity and all existing data remain untouched.
COMMIT;
