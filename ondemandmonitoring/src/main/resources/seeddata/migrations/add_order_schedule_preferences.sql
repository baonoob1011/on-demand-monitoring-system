-- Customer scheduling preferences on orders: recurrence, weather fallback, result deadline.
-- Additive and idempotent; existing orders keep NULLs (treated as NONE / CONTACT_CUSTOMER).
BEGIN;
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS recurrence_type varchar(20);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS recurrence_occurrences integer;
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS weather_fallback varchar(30);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS result_deadline date;
COMMIT;
