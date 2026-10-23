-- Locks request-time pricing to the policy used at submission and persists the authoritative system amount.
-- Safe for existing orders: null policy columns retain the legacy "current ACTIVE policy" fallback.
BEGIN;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pricing_policy_id varchar(36);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pricing_policy_version integer;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS system_calculated_amount numeric(19,0);
ALTER TABLE orders DROP CONSTRAINT IF EXISTS fk_orders_pricing_policy;
ALTER TABLE orders ADD CONSTRAINT fk_orders_pricing_policy
    FOREIGN KEY (pricing_policy_id) REFERENCES pricing_policies(id) ON DELETE RESTRICT;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_pricing_snapshot;
ALTER TABLE orders ADD CONSTRAINT ck_orders_pricing_snapshot CHECK (
    (pricing_policy_id IS NULL AND pricing_policy_version IS NULL)
    OR (pricing_policy_id IS NOT NULL AND pricing_policy_version IS NOT NULL AND pricing_policy_version > 0)
);
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_system_calculated_amount;
ALTER TABLE orders ADD CONSTRAINT ck_orders_system_calculated_amount CHECK (
    system_calculated_amount IS NULL OR system_calculated_amount >= 0
);
CREATE INDEX IF NOT EXISTS ix_orders_pricing_policy ON orders(pricing_policy_id);
COMMIT;
