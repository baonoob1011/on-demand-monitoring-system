-- Apply before enabling checklist bootstrap on an existing database.
-- No Order/Mission/evidence data is rewritten.
BEGIN;
ALTER TABLE public.checklist_definitions ADD COLUMN IF NOT EXISTS seed_code varchar(20);
CREATE UNIQUE INDEX IF NOT EXISTS uk_checklist_seed_code ON public.checklist_definitions(seed_code);
ALTER TABLE public.services ADD COLUMN IF NOT EXISTS checklist_defaults_initialized boolean NOT NULL DEFAULT false;
ALTER TABLE public.services ALTER COLUMN checklist_defaults_initialized SET DEFAULT false;
UPDATE public.services SET checklist_defaults_initialized = false WHERE checklist_defaults_initialized IS NULL;
ALTER TABLE public.services ALTER COLUMN checklist_defaults_initialized SET NOT NULL;
-- Existing administrator-managed templates must not be bootstrapped.
UPDATE public.services s SET checklist_defaults_initialized = true
WHERE EXISTS (SELECT 1 FROM public.service_checklists sc WHERE sc.service_id = s.id);
COMMIT;
