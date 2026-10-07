-- PHASE 6B. Apply after cutover_mission_checklist_assignments.sql, before backend deployment.
-- Isolated verification only during development; do not apply automatically to a shared database.
BEGIN;
LOCK TABLE public.missions IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
    IF EXISTS (SELECT replaces_mission_id FROM public.missions
        WHERE replaces_mission_id IS NOT NULL AND status <> 'CANCELLED'
        GROUP BY replaces_mission_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Amendment migration refused: ambiguous active replacement branches';
    END IF;
END $$;
-- Existing composite same-Order FK remains intact. Cancelled replacement history is retained.
DROP INDEX IF EXISTS public.uk_mission_direct_replacement;
CREATE UNIQUE INDEX IF NOT EXISTS uk_mission_active_direct_replacement
    ON public.missions(replaces_mission_id)
    WHERE replaces_mission_id IS NOT NULL AND status <> 'CANCELLED';
COMMIT;
