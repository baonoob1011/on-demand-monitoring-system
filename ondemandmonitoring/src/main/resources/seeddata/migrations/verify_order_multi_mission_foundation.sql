-- READ ONLY. Run after expansion, and again before runtime cutover.
-- Old runtime can add unlinked executions after migration; rerun backfill or reconcile at cutover.
SELECT
    (SELECT count(*) FROM public.mission_checklist_executions) AS execution_count,
    (SELECT count(*) FROM public.mission_checklist_assignments) AS assignment_count,
    (SELECT count(*) FROM public.mission_checklist_executions WHERE mission_checklist_assignment_id IS NULL) AS unlinked_executions,
    (SELECT count(*) FROM public.mission_checklist_assignments a
        LEFT JOIN public.missions m ON m.id=a.mission_id
        LEFT JOIN public.order_checklist_items i ON i.id=a.order_checklist_item_id
        WHERE m.id IS NULL OR i.id IS NULL OR a.order_id IS DISTINCT FROM m.order_id
            OR a.order_id IS DISTINCT FROM i.order_id) AS invalid_assignments,
    (SELECT count(*) FROM public.mission_checklist_executions e
        LEFT JOIN public.mission_checklist_assignments a ON a.id=e.mission_checklist_assignment_id
        WHERE e.mission_checklist_assignment_id IS NOT NULL AND (a.id IS NULL
            OR e.mission_id IS DISTINCT FROM a.mission_id OR e.order_id IS DISTINCT FROM a.order_id
            OR e.order_checklist_item_id IS DISTINCT FROM a.order_checklist_item_id)) AS invalid_execution_links,
    (SELECT count(*) FROM public.mission_checklist_evidence) AS evidence_count,
    (SELECT count(*) FROM public.drone_media) AS media_count,
    (SELECT count(*) FROM public.mission_results) AS result_count;

-- Candidate report is NOT an approval/finalization command. Never update based on status alone.
-- A candidate still needs review of occurrences, spatial scope and final evidence readiness.
WITH integrity AS (
    SELECT m.id AS mission_id, m.order_id,
        NOT EXISTS (SELECT 1 FROM public.order_checklist_items i WHERE i.order_id=m.order_id
            AND NOT EXISTS (SELECT 1 FROM public.mission_checklist_executions e
                JOIN public.mission_checklist_assignments a ON a.id=e.mission_checklist_assignment_id
                WHERE e.mission_id=m.id AND e.order_id=m.order_id AND e.order_checklist_item_id=i.id
                    AND a.mission_id=m.id AND a.order_id=m.order_id
                    AND a.order_checklist_item_id=i.id AND a.removed_at IS NULL))
        AND NOT EXISTS (SELECT 1 FROM public.mission_checklist_executions e WHERE e.mission_id=m.id
            AND (e.order_id IS DISTINCT FROM m.order_id OR e.mission_checklist_assignment_id IS NULL)) AS complete_integrity
    FROM public.missions m
)
SELECT o.id AS order_id, o.order_status, m.id AS mission_id, m.status AS mission_status,
    o.execution_plan_status, o.execution_plan_revision,
    CASE
        WHEN m.id IS NULL AND o.order_status IN ('PENDING','REJECTED','APPROVED','CANCELLED') THEN 'KEEP_DRAFT'
        WHEN o.order_status IN ('PENDING','REJECTED','CANCELLED') THEN 'REVIEW_INCONSISTENT_OR_CANCELLED'
        WHEN NOT COALESCE(i.complete_integrity,false) THEN 'REVIEW_EXECUTION_INTEGRITY'
        WHEN o.order_status='COMPLETED' AND m.status='COMPLETED' AND EXISTS
            (SELECT 1 FROM public.mission_results r WHERE r.mission_id=m.id AND r.approval_status='APPROVED')
            THEN 'CANDIDATE_FINALIZED_1_REVIEW_EVIDENCE_AND_SCOPE'
        WHEN o.order_status='COMPLETED' THEN 'REVIEW_HISTORICAL_COMPLETION'
        WHEN m.status IN ('CONNECTED','PREFLIGHT_CHECKING','READY_TO_FLY','IN_FLIGHT','IN_PROGRESS',
            'RETURNING','POSTFLIGHT_CHECKING','PENDING_REVIEW','COMPLETED') THEN 'CANDIDATE_FINALIZED_1_REVIEW_SCOPE'
        ELSE 'KEEP_DRAFT_REVIEW_BEFORE_FINALIZE'
    END AS recommended_action
FROM public.orders o LEFT JOIN public.missions m ON m.order_id=o.id
LEFT JOIN integrity i ON i.mission_id=m.id ORDER BY o.id;
