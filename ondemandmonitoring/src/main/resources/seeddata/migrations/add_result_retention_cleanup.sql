-- Result retention. The 90-day update is a one-time legacy backfill, not an application default.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS data_retention_days integer;
UPDATE orders SET data_retention_days = 90 WHERE data_retention_days IS NULL;
ALTER TABLE orders ALTER COLUMN data_retention_days SET NOT NULL;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS result_expires_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_orders_result_expires_at
    ON orders (result_expires_at) WHERE result_expires_at IS NOT NULL;

ALTER TABLE drone_media ADD COLUMN IF NOT EXISTS storage_status varchar(30);
UPDATE drone_media SET storage_status = 'AVAILABLE' WHERE storage_status IS NULL;
ALTER TABLE drone_media ALTER COLUMN storage_status SET NOT NULL;
ALTER TABLE drone_media ADD COLUMN IF NOT EXISTS deleted_from_storage_at timestamptz;
ALTER TABLE drone_media ADD COLUMN IF NOT EXISTS storage_deletion_error varchar(500);

CREATE INDEX IF NOT EXISTS idx_drone_media_storage_status
    ON drone_media (storage_status);
