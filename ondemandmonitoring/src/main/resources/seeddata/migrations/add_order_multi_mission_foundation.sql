-- PHASE 2: EXPAND ONLY. Run manually after the Phase 5/6 migrations, before deploying these entities.
-- No creation/readiness/completion cutover: KEEP uk_missions_order and uk_execution_requirement.
-- Assignment link MUST stay nullable: the unchanged runtime still creates unlinked executions.
-- Rerunnable during this expansion phase; never use as a runtime initializer.
-- Plan metadata stays DRAFT/0. Review candidate classifications in the companion verification SQL;
-- database state alone cannot prove that a manager finalized day/area decomposition.
BEGIN;
LOCK TABLE public.orders, public.missions, public.order_checklist_items,
    public.mission_checklist_executions, public.mission_checklist_evidence,
    public.drone_media, public.mission_results IN ACCESS EXCLUSIVE MODE;

-- Fail before backfill rather than guessing ownership or repairing corrupt history.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM public.mission_checklist_executions e
        LEFT JOIN public.missions m ON m.id=e.mission_id
        LEFT JOIN public.orders o ON o.id=e.order_id
        LEFT JOIN public.order_checklist_items i ON i.id=e.order_checklist_item_id
        WHERE m.id IS NULL OR o.id IS NULL OR i.id IS NULL
           OR e.order_id IS DISTINCT FROM m.order_id OR e.order_id IS DISTINCT FROM i.order_id
           OR e.created_at IS NULL OR i.display_order < 0
    ) THEN
        RAISE EXCEPTION 'Foundation backfill refused: orphan/cross-Order execution or invalid source audit';
    END IF;
    IF EXISTS (SELECT 1 FROM public.missions WHERE order_id IS NULL)
        OR EXISTS (SELECT order_id FROM public.missions GROUP BY order_id HAVING count(*)>1)
        OR EXISTS (SELECT order_checklist_item_id FROM public.mission_checklist_executions
                   GROUP BY order_checklist_item_id HAVING count(*)>1) THEN
        RAISE EXCEPTION 'Foundation requires the current 1:1 gates; use a separate cutover migration';
    END IF;
END $$;

-- Transaction-local preservation snapshots: compare values, not just counts.
CREATE TEMP TABLE foundation_execution_before ON COMMIT DROP AS
    SELECT id, to_jsonb(e)-'mission_checklist_assignment_id' AS data FROM public.mission_checklist_executions e;
CREATE TEMP TABLE foundation_evidence_before ON COMMIT DROP AS SELECT to_jsonb(e) AS data FROM public.mission_checklist_evidence e;
CREATE TEMP TABLE foundation_media_before ON COMMIT DROP AS SELECT to_jsonb(m) AS data FROM public.drone_media m;
CREATE TEMP TABLE foundation_result_before ON COMMIT DROP AS SELECT to_jsonb(r) AS data FROM public.mission_results r;
CREATE TEMP TABLE foundation_snapshot_before ON COMMIT DROP AS SELECT to_jsonb(i) AS data FROM public.order_checklist_items i;

-- EXPAND: nullable legacy actor fields follow the existing scalar-ID audit convention.
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS execution_plan_status varchar(30) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS execution_plan_revision bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS execution_plan_finalized_at timestamptz,
    ADD COLUMN IF NOT EXISTS execution_plan_finalized_by varchar(255),
    ADD COLUMN IF NOT EXISTS execution_plan_amended_at timestamptz,
    ADD COLUMN IF NOT EXISTS execution_plan_amended_by varchar(255),
    ADD COLUMN IF NOT EXISTS execution_plan_amendment_reason varchar(1000);
-- Hibernate-created schemas have no SQL defaults; preserve compatibility with legacy SQL inserts.
ALTER TABLE public.orders ALTER COLUMN execution_plan_status SET DEFAULT 'DRAFT';
ALTER TABLE public.orders ALTER COLUMN execution_plan_revision SET DEFAULT 0;

ALTER TABLE public.missions
    ADD COLUMN IF NOT EXISTS scope_label varchar(255),
    ADD COLUMN IF NOT EXISTS target_point geometry(Point,4326),
    ADD COLUMN IF NOT EXISTS target_area geometry(Polygon,4326),
    ADD COLUMN IF NOT EXISTS finalized_plan_revision bigint,
    ADD COLUMN IF NOT EXISTS creation_idempotency_key varchar(36),
    ADD COLUMN IF NOT EXISTS creation_request_hash varchar(64),
    ADD COLUMN IF NOT EXISTS replaces_mission_id varchar(255),
    ADD COLUMN IF NOT EXISTS cancellation_at timestamptz,
    ADD COLUMN IF NOT EXISTS cancelled_by varchar(255),
    ADD COLUMN IF NOT EXISTS cancellation_reason varchar(1000);

CREATE TABLE IF NOT EXISTS public.mission_checklist_assignments (
    id varchar(255) PRIMARY KEY,
    mission_id varchar(255) NOT NULL,
    order_id varchar(255) NOT NULL,
    order_checklist_item_id varchar(255) NOT NULL,
    display_order integer NOT NULL,
    assigned_by varchar(255),
    assigned_at timestamptz NOT NULL,
    removed_by varchar(255),
    removed_at timestamptz,
    removal_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz,
    version bigint NOT NULL
);
LOCK TABLE public.mission_checklist_assignments IN ACCESS EXCLUSIVE MODE;
ALTER TABLE public.mission_checklist_executions
    ADD COLUMN IF NOT EXISTS mission_checklist_assignment_id varchar(255);

-- Install on both legacy SQL-created and fresh Hibernate-created schemas.
-- All ADD CONSTRAINT statements validate existing rows, failing the whole transaction on corruption.
DO $$ DECLARE entry record; BEGIN
    FOR entry IN SELECT * FROM (VALUES
        ('orders','ck_order_execution_plan',
         'CHECK (execution_plan_status IN (''DRAFT'',''FINALIZED'') AND execution_plan_revision >= 0 AND (execution_plan_status <> ''FINALIZED'' OR (execution_plan_revision > 0 AND execution_plan_finalized_at IS NOT NULL)) AND (execution_plan_finalized_by IS NULL OR execution_plan_finalized_at IS NOT NULL) AND ((execution_plan_amended_at IS NULL AND execution_plan_amended_by IS NULL AND execution_plan_amendment_reason IS NULL) OR (execution_plan_amended_at IS NOT NULL AND execution_plan_amended_by IS NOT NULL AND execution_plan_amendment_reason IS NOT NULL AND length(trim(execution_plan_amendment_reason)) > 0)))'),
        ('missions','uk_missions_order','UNIQUE (order_id)'),
        ('missions','uk_mission_id_order','UNIQUE (id, order_id)'),
        ('order_checklist_items','uk_checklist_item_id_order','UNIQUE (id, order_id)'),
        ('missions','ck_mission_foundation',
         'CHECK ((finalized_plan_revision IS NULL OR finalized_plan_revision >= 0) AND ((creation_idempotency_key IS NULL AND creation_request_hash IS NULL) OR (creation_idempotency_key IS NOT NULL AND creation_request_hash IS NOT NULL AND length(trim(creation_idempotency_key)) > 0 AND creation_request_hash ~ ''^[a-f0-9]{64}$'')) AND (replaces_mission_id IS NULL OR replaces_mission_id <> id) AND (cancelled_by IS NULL OR cancellation_at IS NOT NULL))'),
        ('missions','fk_mission_replaces_order',
         'FOREIGN KEY (replaces_mission_id, order_id) REFERENCES public.missions(id,order_id) ON DELETE RESTRICT'),
        ('mission_checklist_assignments','uk_assignment_identity',
         'UNIQUE (id,mission_id,order_id,order_checklist_item_id)'),
        ('mission_checklist_assignments','fk_assignment_mission_order',
         'FOREIGN KEY (mission_id,order_id) REFERENCES public.missions(id,order_id) ON DELETE RESTRICT'),
        ('mission_checklist_assignments','fk_assignment_item_order',
         'FOREIGN KEY (order_checklist_item_id,order_id) REFERENCES public.order_checklist_items(id,order_id) ON DELETE RESTRICT'),
        ('mission_checklist_assignments','ck_assignment_shape',
         'CHECK (display_order >= 0 AND version >= 0 AND ((removed_at IS NULL AND removed_by IS NULL AND removal_reason IS NULL) OR (removed_at IS NOT NULL AND removed_by IS NOT NULL AND length(trim(removed_by)) > 0 AND removed_at >= assigned_at)))'),
        ('mission_checklist_executions','uk_execution_requirement','UNIQUE (order_checklist_item_id)'),
        ('mission_checklist_executions','uk_execution_assignment','UNIQUE (mission_checklist_assignment_id)'),
        ('mission_checklist_executions','fk_execution_assignment_identity',
         'FOREIGN KEY (mission_checklist_assignment_id,mission_id,order_id,order_checklist_item_id) REFERENCES public.mission_checklist_assignments(id,mission_id,order_id,order_checklist_item_id) ON DELETE RESTRICT')
    ) AS constraints(table_name, constraint_name, definition)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_constraint
            WHERE conrelid=to_regclass('public.' || entry.table_name) AND conname=entry.constraint_name)
            AND NOT (entry.constraint_name='uk_missions_order' AND EXISTS (
                SELECT 1 FROM pg_constraint WHERE conrelid='public.missions'::regclass AND contype='u'
                    AND conkey=ARRAY[(SELECT attnum FROM pg_attribute
                        WHERE attrelid='public.missions'::regclass AND attname='order_id')])) THEN
            EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I %s',
                entry.table_name, entry.constraint_name, entry.definition);
        END IF;
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_assignment_active_pair ON public.mission_checklist_assignments(mission_id,order_checklist_item_id) WHERE removed_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_assignment_mission_order ON public.mission_checklist_assignments(mission_id,display_order,id);
CREATE INDEX IF NOT EXISTS ix_assignment_coverage ON public.mission_checklist_assignments(order_id,order_checklist_item_id);
CREATE INDEX IF NOT EXISTS ix_mission_order_schedule ON public.missions(order_id,scheduled_start_at,id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_mission_creation_command ON public.missions(order_id,creation_idempotency_key) WHERE creation_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_mission_direct_replacement ON public.missions(replaces_mission_id) WHERE replaces_mission_id IS NOT NULL;

-- BACKFILL: stable UUID text derived from execution ID, no extension or fabricated actor needed.
-- Reuse an already active same-identity assignment when rerunning a partially expanded schema.
INSERT INTO public.mission_checklist_assignments
    (id,mission_id,order_id,order_checklist_item_id,display_order,assigned_at,created_at,version)
SELECT md5('odms:legacy-assignment:' || e.id)::uuid::text,
    e.mission_id,e.order_id,e.order_checklist_item_id,i.display_order,e.created_at,e.created_at,0
FROM public.mission_checklist_executions e
JOIN public.order_checklist_items i ON i.id=e.order_checklist_item_id
WHERE e.mission_checklist_assignment_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM public.mission_checklist_assignments a
      WHERE a.mission_id=e.mission_id AND a.order_id=e.order_id
        AND a.order_checklist_item_id=e.order_checklist_item_id AND a.removed_at IS NULL);

UPDATE public.mission_checklist_executions e
SET mission_checklist_assignment_id=a.id
FROM public.mission_checklist_assignments a
WHERE e.mission_checklist_assignment_id IS NULL AND a.removed_at IS NULL
  AND a.mission_id=e.mission_id AND a.order_id=e.order_id
  AND a.order_checklist_item_id=e.order_checklist_item_id;

-- Copy once into absent scope fields. Do not overwrite a non-null custom scope on rerun.
UPDATE public.missions m SET target_point=o.point, target_area=o.target_area
FROM public.orders o WHERE m.order_id=o.id AND m.target_point IS NULL AND m.target_area IS NULL
    AND m.scope_label IS NULL AND m.creation_idempotency_key IS NULL;
-- scope_label/finalization audit remain unknown; never invent "Day 1" or a manager approval.

-- VERIFY: preserve original execution columns and every evidence/media/result/snapshot value.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM public.mission_checklist_executions e
        LEFT JOIN public.mission_checklist_assignments a ON a.id=e.mission_checklist_assignment_id
        WHERE a.id IS NULL OR a.mission_id IS DISTINCT FROM e.mission_id
            OR a.order_id IS DISTINCT FROM e.order_id
            OR a.order_checklist_item_id IS DISTINCT FROM e.order_checklist_item_id OR a.removed_at IS NOT NULL)
        OR (SELECT count(DISTINCT mission_checklist_assignment_id) FROM public.mission_checklist_executions)
            <> (SELECT count(*) FROM public.mission_checklist_executions) THEN
        RAISE EXCEPTION 'Foundation verification failed: assignment linkage';
    END IF;
    IF EXISTS ((SELECT id,data FROM foundation_execution_before EXCEPT SELECT id,to_jsonb(e)-'mission_checklist_assignment_id' FROM public.mission_checklist_executions e)
        UNION ALL (SELECT id,to_jsonb(e)-'mission_checklist_assignment_id' FROM public.mission_checklist_executions e EXCEPT SELECT id,data FROM foundation_execution_before))
        OR EXISTS ((SELECT data FROM foundation_evidence_before EXCEPT SELECT to_jsonb(e) FROM public.mission_checklist_evidence e)
            UNION ALL (SELECT to_jsonb(e) FROM public.mission_checklist_evidence e EXCEPT SELECT data FROM foundation_evidence_before))
        OR EXISTS ((SELECT data FROM foundation_media_before EXCEPT SELECT to_jsonb(m) FROM public.drone_media m)
            UNION ALL (SELECT to_jsonb(m) FROM public.drone_media m EXCEPT SELECT data FROM foundation_media_before))
        OR EXISTS ((SELECT data FROM foundation_result_before EXCEPT SELECT to_jsonb(r) FROM public.mission_results r)
            UNION ALL (SELECT to_jsonb(r) FROM public.mission_results r EXCEPT SELECT data FROM foundation_result_before))
        OR EXISTS ((SELECT data FROM foundation_snapshot_before EXCEPT SELECT to_jsonb(i) FROM public.order_checklist_items i)
            UNION ALL (SELECT to_jsonb(i) FROM public.order_checklist_items i EXCEPT SELECT data FROM foundation_snapshot_before)) THEN
        RAISE EXCEPTION 'Foundation verification failed: historical data changed';
    END IF;
END $$;
-- ENFORCE is intentionally limited to nullable composite FK and current 1:1 gates.
-- A subsequent cutover must handle new unlinked executions before SET NOT NULL / unique removal.
COMMIT;
