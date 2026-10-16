-- Checklist evidence requirement policy V2.
-- Safe for existing databases: legacy V0/V1 records retain their stored meanings.
-- Run once per deployment after the application has created the required JPA column.
BEGIN;

ALTER TABLE public.order_checklist_items
    ADD COLUMN IF NOT EXISTS required_evidence_type varchar(30);

-- The legacy policy had no media-type dimension and accepted either IMAGE or VIDEO.
-- Backfill V0/V1 to that exact behavior without changing their count or version.
UPDATE public.order_checklist_items
SET required_evidence_type = 'IMAGE_OR_VIDEO'
WHERE required_evidence_type IS NULL;

-- Refuse unexpected snapshots rather than silently inventing requirements.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM public.order_checklist_items
        WHERE required_evidence_type IS NULL
           OR (evidence_policy_version = 0 AND minimum_evidence_count <> 0)
           OR (evidence_policy_version = 1 AND minimum_evidence_count <> 1)
           OR (evidence_policy_version = 2 AND (minimum_evidence_count < 1 OR minimum_evidence_count > 50
               OR required_evidence_type NOT IN ('IMAGE','VIDEO','IMAGE_OR_VIDEO')))
           OR evidence_policy_version NOT IN (0,1,2)
    ) THEN
        RAISE EXCEPTION 'Unsupported order checklist evidence snapshot; inspect rows before migration';
    END IF;
END $$;

ALTER TABLE public.order_checklist_items
    ALTER COLUMN required_evidence_type SET DEFAULT 'IMAGE_OR_VIDEO',
    ALTER COLUMN required_evidence_type SET NOT NULL;

ALTER TABLE public.order_checklist_items
    DROP CONSTRAINT IF EXISTS ck_snapshot_evidence_policy;
ALTER TABLE public.order_checklist_items
    ADD CONSTRAINT ck_snapshot_evidence_policy CHECK (
        (evidence_policy_version = 0 AND minimum_evidence_count = 0)
        OR (evidence_policy_version = 1 AND minimum_evidence_count = 1 AND required_evidence_type = 'IMAGE_OR_VIDEO')
        OR (evidence_policy_version = 2 AND minimum_evidence_count BETWEEN 1 AND 50
            AND required_evidence_type IN ('IMAGE','VIDEO','IMAGE_OR_VIDEO'))
    );

COMMIT;
