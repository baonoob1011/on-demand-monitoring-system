ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS requested_scope_feasible BOOLEAN;

-- Hibernate creates an enum CHECK constraint but does not expand it when a new
-- Java enum value is introduced. Keep existing databases compatible as well.
ALTER TABLE order_review_records
    DROP CONSTRAINT IF EXISTS order_review_records_criterion_code_check;

ALTER TABLE order_review_records
    ADD CONSTRAINT order_review_records_criterion_code_check CHECK (
        criterion_code IN (
            'SERVICE_AREA', 'CHECKLIST_REVIEWED', 'SITE_CONTACT', 'SCHEDULE_DATE',
            'FLIGHT_SCALE', 'SENSITIVE_AREA', 'AIRSPACE_PERMIT', 'SITE_ACCESS',
            'CAPABILITY', 'REQUESTED_SCOPE_FEASIBLE', 'PURPOSE', 'RISK_LEVEL',
            'PRICE_REASONABLE', 'SURVEY_NEEDED'
        )
    );
