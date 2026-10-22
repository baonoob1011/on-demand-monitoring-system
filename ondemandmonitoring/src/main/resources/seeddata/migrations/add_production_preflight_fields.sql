-- Persistent evidence and audit metadata for the production pre-flight checklist.
-- Idempotent so it can be applied safely to an existing PostgreSQL database.
ALTER TABLE IF EXISTS persisted_pre_device_check_item
    ADD COLUMN IF NOT EXISTS description varchar(500),
    ADD COLUMN IF NOT EXISTS category varchar(80),
    ADD COLUMN IF NOT EXISTS required boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS source varchar(20),
    ADD COLUMN IF NOT EXISTS measured_value varchar(1000),
    ADD COLUMN IF NOT EXISTS error_code varchar(80),
    ADD COLUMN IF NOT EXISTS checked_by varchar(255),
    ADD COLUMN IF NOT EXISTS expires_at timestamptz;

UPDATE persisted_pre_device_check_item
SET description = COALESCE(description, check_name),
    category = COALESCE(category, 'DỮ LIỆU CŨ'),
    source = COALESCE(source, 'MANUAL')
WHERE description IS NULL OR category IS NULL OR source IS NULL;

ALTER TABLE IF EXISTS persisted_pre_device_check_item
    ALTER COLUMN description SET NOT NULL,
    ALTER COLUMN category SET NOT NULL,
    ALTER COLUMN source SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_preflight_item_run_status
    ON persisted_pre_device_check_item(pre_device_run_id, status);
