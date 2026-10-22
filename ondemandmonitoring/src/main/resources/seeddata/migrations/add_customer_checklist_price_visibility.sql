-- Customer-safe checklist price visibility lifecycle.
-- Additive and idempotent; preserves every internal pricing/audit amount.
BEGIN;

ALTER TABLE public.order_checklist_items
    ADD COLUMN IF NOT EXISTS pricing_status varchar(40);

-- Backfill from the existing source/review/final-price lifecycle. Customer-added
-- rows are never considered approved unless a final Manager amount already exists.
UPDATE public.order_checklist_items
SET pricing_status = CASE
    WHEN source_type = 'SERVICE_TEMPLATE' THEN 'AUTO_PRICED'
    WHEN review_status = 'DUPLICATE' THEN 'DUPLICATE'
    WHEN review_status = 'REJECTED' THEN 'REJECTED'
    WHEN review_status = 'INCLUDED' THEN 'DUPLICATE'
    WHEN review_status = 'ADDITIONAL' AND final_amount IS NOT NULL
        THEN CASE
            WHEN final_amount = system_calculated_price THEN 'APPROVED'
            ELSE 'ADJUSTED_AND_APPROVED'
        END
    ELSE 'NEEDS_MANAGER_REVIEW'
END
WHERE pricing_status IS NULL;

ALTER TABLE public.order_checklist_items
    ALTER COLUMN pricing_status SET DEFAULT 'NEEDS_MANAGER_REVIEW',
    ALTER COLUMN pricing_status SET NOT NULL;

COMMIT;
