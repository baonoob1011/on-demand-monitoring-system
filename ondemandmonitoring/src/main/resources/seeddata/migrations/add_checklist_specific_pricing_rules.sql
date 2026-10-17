-- Optional checklist target for extra-image/video pricing rules.
BEGIN;

ALTER TABLE public.pricing_rules
    ADD COLUMN IF NOT EXISTS checklist_definition_id varchar(36);

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.pricing_rules'::regclass
          AND conname = 'fk_pricing_rule_checklist_definition'
    ) THEN
        ALTER TABLE public.pricing_rules
            ADD CONSTRAINT fk_pricing_rule_checklist_definition
            FOREIGN KEY (checklist_definition_id)
            REFERENCES public.checklist_definitions(id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_pricing_rules_checklist_media
    ON public.pricing_rules(policy_id, checklist_definition_id, media_component)
    WHERE checklist_definition_id IS NOT NULL;

COMMIT;
