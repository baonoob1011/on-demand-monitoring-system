-- Phase 6 deployment migration. Run before starting Phase 6 application code.
-- Existing snapshots remain policy 0; application capture explicitly writes policy 1.
BEGIN;
LOCK TABLE public.order_checklist_items, public.mission_checklist_executions, public.drone_media IN ACCESS EXCLUSIVE MODE;
ALTER TABLE public.order_checklist_items ADD COLUMN IF NOT EXISTS evidence_policy_version integer NOT NULL DEFAULT 0;
ALTER TABLE public.order_checklist_items ADD COLUMN IF NOT EXISTS minimum_evidence_count integer NOT NULL DEFAULT 0;
-- ddl-auto=create may have created the columns without defaults. SQL legacy inserts remain policy 0.
ALTER TABLE public.order_checklist_items ALTER COLUMN evidence_policy_version SET DEFAULT 0;
ALTER TABLE public.order_checklist_items ALTER COLUMN minimum_evidence_count SET DEFAULT 0;
UPDATE public.order_checklist_items SET evidence_policy_version=0 WHERE evidence_policy_version IS NULL;
UPDATE public.order_checklist_items SET minimum_evidence_count=0 WHERE minimum_evidence_count IS NULL;
ALTER TABLE public.order_checklist_items ALTER COLUMN evidence_policy_version SET NOT NULL;
ALTER TABLE public.order_checklist_items ALTER COLUMN minimum_evidence_count SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.order_checklist_items'::regclass AND conname='ck_snapshot_evidence_policy') THEN
        ALTER TABLE public.order_checklist_items ADD CONSTRAINT ck_snapshot_evidence_policy
            CHECK ((evidence_policy_version=0 AND minimum_evidence_count=0) OR (evidence_policy_version=1 AND minimum_evidence_count=1));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.mission_checklist_executions'::regclass AND conname='uk_execution_id_mission') THEN
        ALTER TABLE public.mission_checklist_executions ADD CONSTRAINT uk_execution_id_mission UNIQUE(id, mission_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.drone_media'::regclass AND conname='uk_media_id_mission') THEN
        ALTER TABLE public.drone_media ADD CONSTRAINT uk_media_id_mission UNIQUE(id, mission_id);
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS public.mission_checklist_evidence (
    id varchar(255) PRIMARY KEY,
    mission_id varchar(255) NOT NULL,
    mission_checklist_execution_id varchar(255) NOT NULL,
    media_asset_id varchar(255) NOT NULL,
    attached_by varchar(255) NOT NULL,
    attached_at timestamptz NOT NULL,
    note varchar(1000),
    detached_by varchar(255),
    detached_at timestamptz,
    detach_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz,
    version bigint NOT NULL
);
-- Also install constraints after Hibernate-created development schemas.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.mission_checklist_evidence'::regclass AND conname='fk_evidence_execution_mission') THEN
        ALTER TABLE public.mission_checklist_evidence ADD CONSTRAINT fk_evidence_execution_mission
            FOREIGN KEY(mission_checklist_execution_id, mission_id) REFERENCES public.mission_checklist_executions(id, mission_id) ON DELETE RESTRICT;
        ALTER TABLE public.mission_checklist_evidence ADD CONSTRAINT fk_evidence_media_mission
            FOREIGN KEY(media_asset_id, mission_id) REFERENCES public.drone_media(id, mission_id) ON DELETE RESTRICT;
        ALTER TABLE public.mission_checklist_evidence ADD CONSTRAINT fk_evidence_attached_user FOREIGN KEY(attached_by) REFERENCES public.users(id) ON DELETE RESTRICT;
        ALTER TABLE public.mission_checklist_evidence ADD CONSTRAINT fk_evidence_detached_user FOREIGN KEY(detached_by) REFERENCES public.users(id) ON DELETE RESTRICT;
        ALTER TABLE public.mission_checklist_evidence ADD CONSTRAINT ck_evidence_detach
            CHECK ((detached_at IS NULL AND detached_by IS NULL AND detach_reason IS NULL)
                OR (detached_at IS NOT NULL AND detached_by IS NOT NULL AND detached_at >= attached_at));
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uk_evidence_active_pair ON public.mission_checklist_evidence(mission_checklist_execution_id, media_asset_id) WHERE detached_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_evidence_mission_execution ON public.mission_checklist_evidence(mission_id, mission_checklist_execution_id);
CREATE INDEX IF NOT EXISTS ix_evidence_media ON public.mission_checklist_evidence(media_asset_id);
COMMIT;
