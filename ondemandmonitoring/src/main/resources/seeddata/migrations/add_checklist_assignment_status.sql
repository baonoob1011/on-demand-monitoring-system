-- Checklist assignment tracking: one OrderChecklistItem belongs to at most one effective Mission.
ALTER TABLE order_checklist_items
    ADD COLUMN IF NOT EXISTS assignment_status varchar(20) NOT NULL DEFAULT 'UNASSIGNED';

-- Backfill from active assignments on non-cancelled Missions.
UPDATE order_checklist_items i
SET assignment_status = CASE
        WHEN m.status = 'COMPLETED' THEN 'COMPLETED'
        WHEN m.status IN ('IN_FLIGHT', 'IN_PROGRESS', 'RETURNING', 'POSTFLIGHT_CHECKING', 'PENDING_REVIEW', 'FAILED') THEN 'IN_PROGRESS'
        ELSE 'ASSIGNED'
    END
FROM mission_checklist_assignments a
JOIN missions m ON m.id = a.mission_id
WHERE a.order_checklist_item_id = i.id
  AND a.removed_at IS NULL
  AND m.status <> 'CANCELLED'
  AND m.cancellation_at IS NULL;

-- Items already assigned to more than one effective Mission must be resolved manually before the rule is enforced.
SELECT a.order_checklist_item_id, count(*) AS effective_missions
FROM mission_checklist_assignments a
JOIN missions m ON m.id = a.mission_id
WHERE a.removed_at IS NULL AND m.status <> 'CANCELLED' AND m.cancellation_at IS NULL
GROUP BY a.order_checklist_item_id
HAVING count(*) > 1;
