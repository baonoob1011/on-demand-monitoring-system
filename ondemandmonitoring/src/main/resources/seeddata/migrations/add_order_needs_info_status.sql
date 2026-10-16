BEGIN;

ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS orders_order_status_check;
ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS ck_orders_status;
ALTER TABLE public.orders
    ADD CONSTRAINT ck_orders_status
    CHECK (order_status IN ('PENDING', 'NEEDS_INFO', 'APPROVED', 'REJECTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

COMMIT;
