ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS requested_update_scope varchar(40);
