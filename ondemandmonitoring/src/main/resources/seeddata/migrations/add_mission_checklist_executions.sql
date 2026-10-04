-- Phase 5. Apply after the Order snapshot and Order-Mission 1:1 migrations.
-- No historical backfill: missing execution rows on existing missions need a deliberate rollout.
-- Run with a deployment role, not at application startup. No data is deleted or inferred.
BEGIN;
LOCK TABLE public.missions, public.order_checklist_items, public.mission_results IN ACCESS EXCLUSIVE MODE;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.missions'::regclass AND conname = 'uk_mission_id_order') THEN
        ALTER TABLE public.missions ADD CONSTRAINT uk_mission_id_order UNIQUE (id, order_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.order_checklist_items'::regclass AND conname = 'uk_checklist_item_id_order') THEN
        ALTER TABLE public.order_checklist_items ADD CONSTRAINT uk_checklist_item_id_order UNIQUE (id, order_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.mission_checklist_executions (
    id varchar(255) PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz,
    version bigint NOT NULL,
    mission_id varchar(255) NOT NULL,
    order_id varchar(255) NOT NULL,
    order_checklist_item_id varchar(255) NOT NULL,
    execution_status varchar(30) NOT NULL DEFAULT 'PENDING',
    assessment_status varchar(30) NOT NULL DEFAULT 'NOT_ASSESSED',
    observation varchar(2000),
    unable_to_verify_reason varchar(1000),
    started_at timestamptz,
    completed_at timestamptz,
    last_modified_by varchar(255),
    CONSTRAINT uk_execution_requirement UNIQUE (order_checklist_item_id),
    CONSTRAINT fk_execution_mission_order FOREIGN KEY (mission_id, order_id) REFERENCES public.missions (id, order_id),
    CONSTRAINT fk_execution_item_order FOREIGN KEY (order_checklist_item_id, order_id) REFERENCES public.order_checklist_items (id, order_id),
    CONSTRAINT ck_execution_status CHECK (execution_status IN ('PENDING','IN_PROGRESS','COMPLETED','UNABLE_TO_VERIFY')),
    CONSTRAINT ck_execution_assessment CHECK (assessment_status IN ('NOT_ASSESSED','COMPLIANT','NON_COMPLIANT')),
    CONSTRAINT ck_execution_reason CHECK (
        execution_status = 'UNABLE_TO_VERIFY' AND unable_to_verify_reason IS NOT NULL AND length(trim(unable_to_verify_reason)) > 0
        OR execution_status <> 'UNABLE_TO_VERIFY' AND unable_to_verify_reason IS NULL),
    CONSTRAINT ck_execution_times CHECK (
        execution_status IN ('COMPLETED','UNABLE_TO_VERIFY') AND started_at IS NOT NULL AND completed_at IS NOT NULL
        OR execution_status = 'IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL
        OR execution_status = 'PENDING' AND started_at IS NULL AND completed_at IS NULL)
);
CREATE INDEX IF NOT EXISTS ix_execution_mission ON public.mission_checklist_executions (mission_id);

-- Also enforce composite FKs when ddl-auto=create has already created the table.
-- JPA associations reference the normal entity identifiers; this migration owns cross-order integrity.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.mission_checklist_executions'::regclass AND conname = 'fk_execution_mission_order') THEN
        ALTER TABLE public.mission_checklist_executions ADD CONSTRAINT fk_execution_mission_order
            FOREIGN KEY (mission_id, order_id) REFERENCES public.missions (id, order_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.mission_checklist_executions'::regclass AND conname = 'fk_execution_item_order') THEN
        ALTER TABLE public.mission_checklist_executions ADD CONSTRAINT fk_execution_item_order
            FOREIGN KEY (order_checklist_item_id, order_id) REFERENCES public.order_checklist_items (id, order_id);
    END IF;
END $$;

-- Hibernate-generated enum CHECK names vary; replace only checks on this specific column.
DO $$ DECLARE constraint_name text; BEGIN
    FOR constraint_name IN SELECT conname FROM pg_constraint
        WHERE conrelid = 'public.mission_results'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%approval_status%'
    LOOP
        EXECUTE format('ALTER TABLE public.mission_results DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
ALTER TABLE public.mission_results ALTER COLUMN submitted_at DROP NOT NULL;
ALTER TABLE public.mission_results ADD CONSTRAINT ck_result_approval_status
    CHECK (approval_status IN ('DRAFT','PENDING_MANAGER_APPROVAL','APPROVED','REJECTED'));
COMMIT;
