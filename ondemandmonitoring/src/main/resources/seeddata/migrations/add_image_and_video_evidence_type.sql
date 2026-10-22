-- Adds required_evidence_type = 'IMAGE_AND_VIDEO' for order checklist snapshots (evidence policy V3).
--
-- IMAGE_AND_VIDEO means the checklist needs its own minimum number of images AND its own minimum number of videos
-- (minimum_image_count / minimum_video_count). 'IMAGE_OR_VIDEO' stays valid so historical V1/V2 snapshots keep their meaning;
-- new orders no longer create it. Existing V3 snapshots that stored 'IMAGE_OR_VIDEO' for "images and videos" are rewritten.
--
-- Apply AFTER add_flexible_checklist_pricing.sql, once per database, before deploying the matching application version.
BEGIN;

-- Hibernate may have generated its own enum check on the column (name varies); drop it, the policy check below is authoritative.
DO $$ DECLARE c record; BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'public.order_checklist_items'::regclass AND contype = 'c'
               AND conname <> 'ck_snapshot_evidence_policy'
               AND pg_get_constraintdef(oid) LIKE '%required_evidence_type%'
               AND pg_get_constraintdef(oid) NOT LIKE '%evidence_policy_version%' LOOP
        EXECUTE format('ALTER TABLE public.order_checklist_items DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE public.order_checklist_items DROP CONSTRAINT IF EXISTS ck_snapshot_evidence_policy;

-- V3 rows that need both types were stored with the legacy value; express them with the new one.
UPDATE public.order_checklist_items
SET required_evidence_type = 'IMAGE_AND_VIDEO'
WHERE evidence_policy_version = 3 AND minimum_image_count > 0 AND minimum_video_count > 0
  AND required_evidence_type = 'IMAGE_OR_VIDEO';

ALTER TABLE public.order_checklist_items ADD CONSTRAINT ck_snapshot_evidence_policy CHECK (
    (evidence_policy_version = 0 AND minimum_evidence_count = 0 AND minimum_image_count = 0 AND minimum_video_count = 0)
    OR (evidence_policy_version = 1 AND minimum_evidence_count = 1 AND required_evidence_type = 'IMAGE_OR_VIDEO'
        AND minimum_image_count = 0 AND minimum_video_count = 0)
    OR (evidence_policy_version = 2 AND minimum_evidence_count BETWEEN 1 AND 50
        AND required_evidence_type IN ('IMAGE','VIDEO','IMAGE_OR_VIDEO') AND minimum_image_count = 0 AND minimum_video_count = 0)
    OR (evidence_policy_version = 3 AND minimum_image_count >= 0 AND minimum_video_count >= 0
        AND minimum_evidence_count = minimum_image_count + minimum_video_count AND minimum_evidence_count BETWEEN 1 AND 1000
        AND required_evidence_type IN ('IMAGE','VIDEO','IMAGE_AND_VIDEO','IMAGE_OR_VIDEO')));

COMMIT;
