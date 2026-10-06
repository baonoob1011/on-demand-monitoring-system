-- PHASE 5. Apply AFTER enable_order_multi_mission_creation.sql, BEFORE backend deployment.
-- Maintenance window required. Never rerun expansion/old 1:1 migrations after this cutover.
-- Anomalies abort the transaction for explicit manual classification; no history is reconstructed.
BEGIN;
LOCK TABLE public.orders, public.missions, public.order_checklist_items,
    public.mission_checklist_assignments, public.mission_checklist_executions,
    public.mission_checklist_evidence, public.drone_media, public.mission_results IN ACCESS EXCLUSIVE MODE;

CREATE TEMP TABLE cutover_execution_before ON COMMIT DROP AS
    SELECT id,to_jsonb(e)-'mission_checklist_assignment_id' AS data FROM public.mission_checklist_executions e;
CREATE TEMP TABLE cutover_history_before ON COMMIT DROP AS
    SELECT 'evidence' AS kind,to_jsonb(e) AS data FROM public.mission_checklist_evidence e
    UNION ALL SELECT 'media',to_jsonb(m) FROM public.drone_media m
    UNION ALL SELECT 'result',to_jsonb(r) FROM public.mission_results r
    UNION ALL SELECT 'snapshot',to_jsonb(i) FROM public.order_checklist_items i;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM public.mission_checklist_executions e
        LEFT JOIN public.missions m ON m.id=e.mission_id
        LEFT JOIN public.order_checklist_items i ON i.id=e.order_checklist_item_id
        WHERE m.id IS NULL OR i.id IS NULL OR e.order_id IS DISTINCT FROM m.order_id
            OR e.order_id IS DISTINCT FROM i.order_id OR e.created_at IS NULL
            OR (e.mission_checklist_assignment_id IS NULL AND m.creation_idempotency_key IS NOT NULL)) THEN
        RAISE EXCEPTION 'Cutover refused: orphan/cross-Order execution or unlinked planning execution';
    END IF;
    IF EXISTS (SELECT 1 FROM public.missions legacy JOIN public.missions sibling
        ON sibling.order_id=legacy.order_id AND sibling.id<>legacy.id WHERE legacy.creation_idempotency_key IS NULL) THEN
        RAISE EXCEPTION 'Cutover refused: legacy Order has siblings; manual classification required';
    END IF;
    -- Legacy whole-Order expectation is checked ONCE during migration, never at runtime.
    IF EXISTS (SELECT 1 FROM public.missions m JOIN public.order_checklist_items i ON i.order_id=m.order_id
        WHERE m.creation_idempotency_key IS NULL AND NOT EXISTS (
            SELECT 1 FROM public.mission_checklist_executions e WHERE e.mission_id=m.id AND e.order_checklist_item_id=i.id)) THEN
        RAISE EXCEPTION 'Cutover refused: incomplete legacy execution package; manual classification required';
    END IF;
    IF EXISTS (SELECT 1 FROM public.mission_checklist_executions e
        LEFT JOIN public.mission_checklist_assignments a ON a.id=e.mission_checklist_assignment_id
        WHERE e.mission_checklist_assignment_id IS NOT NULL AND (a.id IS NULL OR a.removed_at IS NOT NULL
            OR a.mission_id IS DISTINCT FROM e.mission_id OR a.order_id IS DISTINCT FROM e.order_id
            OR a.order_checklist_item_id IS DISTINCT FROM e.order_checklist_item_id)) THEN
        RAISE EXCEPTION 'Cutover refused: invalid existing Assignment identity/history';
    END IF;
END $$;

INSERT INTO public.mission_checklist_assignments
    (id,mission_id,order_id,order_checklist_item_id,display_order,assigned_at,created_at,version)
SELECT md5('odms:legacy-assignment:' || e.id)::uuid::text,
    e.mission_id,e.order_id,e.order_checklist_item_id,i.display_order,e.created_at,e.created_at,0
FROM public.mission_checklist_executions e JOIN public.order_checklist_items i ON i.id=e.order_checklist_item_id
WHERE e.mission_checklist_assignment_id IS NULL AND NOT EXISTS (
    SELECT 1 FROM public.mission_checklist_assignments a WHERE a.mission_id=e.mission_id
        AND a.order_id=e.order_id AND a.order_checklist_item_id=e.order_checklist_item_id AND a.removed_at IS NULL);
UPDATE public.mission_checklist_executions e SET mission_checklist_assignment_id=a.id
FROM public.mission_checklist_assignments a
WHERE e.mission_checklist_assignment_id IS NULL AND a.removed_at IS NULL
    AND a.mission_id=e.mission_id AND a.order_id=e.order_id AND a.order_checklist_item_id=e.order_checklist_item_id;

DO $$ DECLARE entry record; requirement smallint; BEGIN
    IF EXISTS (SELECT 1 FROM public.mission_checklist_executions WHERE mission_checklist_assignment_id IS NULL)
        OR EXISTS (SELECT mission_checklist_assignment_id FROM public.mission_checklist_executions
            GROUP BY mission_checklist_assignment_id HAVING count(*)>1)
        OR EXISTS (SELECT 1 FROM public.mission_checklist_assignments a JOIN public.missions m ON m.id=a.mission_id
            WHERE m.creation_idempotency_key IS NULL AND a.removed_at IS NULL AND NOT EXISTS (
                SELECT 1 FROM public.mission_checklist_executions e WHERE e.mission_checklist_assignment_id=a.id)) THEN
        RAISE EXCEPTION 'Cutover refused: incomplete Assignment/Execution bijection';
    END IF;
    SELECT attnum INTO requirement FROM pg_attribute
        WHERE attrelid='public.mission_checklist_executions'::regclass AND attname='order_checklist_item_id';
    -- Remove every single-column global unique, including Hibernate-generated names.
    FOR entry IN SELECT conname FROM pg_constraint WHERE conrelid='public.mission_checklist_executions'::regclass
        AND contype='u' AND conkey=ARRAY[requirement] LOOP
        EXECUTE format('ALTER TABLE public.mission_checklist_executions DROP CONSTRAINT %I',entry.conname);
    END LOOP;
    FOR entry IN SELECT indexrelid::regclass AS index_name FROM pg_index
        WHERE indrelid='public.mission_checklist_executions'::regclass AND indisunique
            AND indnkeyatts=1 AND (indkey::smallint[])[0]=requirement LOOP
        EXECUTE format('DROP INDEX %s',entry.index_name);
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.mission_checklist_executions'::regclass
        AND conname='uk_execution_assignment') THEN
        ALTER TABLE public.mission_checklist_executions ADD CONSTRAINT uk_execution_assignment UNIQUE (mission_checklist_assignment_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.mission_checklist_executions'::regclass
        AND conname='fk_execution_assignment_identity') THEN
        ALTER TABLE public.mission_checklist_executions ADD CONSTRAINT fk_execution_assignment_identity
            FOREIGN KEY (mission_checklist_assignment_id,mission_id,order_id,order_checklist_item_id)
            REFERENCES public.mission_checklist_assignments(id,mission_id,order_id,order_checklist_item_id) ON DELETE RESTRICT;
    END IF;
END $$;
ALTER TABLE public.mission_checklist_executions VALIDATE CONSTRAINT fk_execution_assignment_identity;
ALTER TABLE public.mission_checklist_executions ALTER COLUMN mission_checklist_assignment_id SET NOT NULL;

CREATE TEMP TABLE cutover_history_after ON COMMIT DROP AS
    SELECT 'evidence' AS kind,to_jsonb(e) AS data FROM public.mission_checklist_evidence e
    UNION ALL SELECT 'media',to_jsonb(m) FROM public.drone_media m
    UNION ALL SELECT 'result',to_jsonb(r) FROM public.mission_results r
    UNION ALL SELECT 'snapshot',to_jsonb(i) FROM public.order_checklist_items i;
DO $$ BEGIN
    IF EXISTS ((SELECT id,data FROM cutover_execution_before EXCEPT SELECT id,to_jsonb(e)-'mission_checklist_assignment_id' FROM public.mission_checklist_executions e)
        UNION ALL (SELECT id,to_jsonb(e)-'mission_checklist_assignment_id' FROM public.mission_checklist_executions e EXCEPT SELECT id,data FROM cutover_execution_before))
        OR EXISTS ((SELECT kind,data FROM cutover_history_before EXCEPT SELECT kind,data FROM cutover_history_after)
            UNION ALL (SELECT kind,data FROM cutover_history_after EXCEPT SELECT kind,data FROM cutover_history_before)) THEN
        RAISE EXCEPTION 'Cutover verification failed: historical values changed';
    END IF;
END $$;
-- No Order/Mission status/key/revision changes, no evidence FK drops, no fabricated manager audit.
COMMIT;
