-- Flexible Admin-configured checklist & service pricing.
-- Additive and idempotent. Existing rows keep their meaning:
--   * checklists get price 0 / allowance 0, so every existing Service and Order price is unchanged;
--   * order checklist snapshots keep pricing_snapshot_version 0 (priced by difficulty only) and their evidence policy V0-V2;
--   * published pricing policies are untouched (no MEDIA rules are invented).
-- Run once per deployment BEFORE bootstrap_after_hibernate.sql / verify_current_schema.sql.
-- Never run against the shared development database from an automated task; test on an isolated database.
BEGIN;

-- 1. Checklist catalog -------------------------------------------------------------------------------------------
ALTER TABLE public.checklist_definitions
    ADD COLUMN IF NOT EXISTS base_price numeric(19,0) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS included_image_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS included_video_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS standard_video_duration_seconds integer,
    ADD COLUMN IF NOT EXISTS pricing_profile varchar(60);

ALTER TABLE public.checklist_definitions DROP CONSTRAINT IF EXISTS ck_checklist_pricing;
ALTER TABLE public.checklist_definitions ADD CONSTRAINT ck_checklist_pricing CHECK (
    base_price >= 0 AND included_image_count BETWEEN 0 AND 500 AND included_video_count BETWEEN 0 AND 500
    AND (standard_video_duration_seconds IS NULL OR standard_video_duration_seconds BETWEEN 1 AND 7200));

-- 1b. Stored default price of a service (sum of its default checklists); recomputed by the application, NULL until then.
ALTER TABLE public.services
    ADD COLUMN IF NOT EXISTS default_price numeric(19,0),
    ADD COLUMN IF NOT EXISTS default_price_computed_at timestamp(6) with time zone;
ALTER TABLE public.services DROP CONSTRAINT IF EXISTS ck_services_default_price;
ALTER TABLE public.services ADD CONSTRAINT ck_services_default_price CHECK (default_price IS NULL OR default_price >= 0);

-- 2. Service-checklist association: per-service overrides, required flag ------------------------------------------
ALTER TABLE public.service_checklists
    ADD COLUMN IF NOT EXISTS base_price_override numeric(19,0),
    ADD COLUMN IF NOT EXISTS included_image_count_override integer,
    ADD COLUMN IF NOT EXISTS included_video_count_override integer,
    ADD COLUMN IF NOT EXISTS pricing_profile_override varchar(60),
    ADD COLUMN IF NOT EXISTS is_required boolean NOT NULL DEFAULT false;

ALTER TABLE public.service_checklists DROP CONSTRAINT IF EXISTS ck_service_checklist_overrides;
ALTER TABLE public.service_checklists ADD CONSTRAINT ck_service_checklist_overrides CHECK (
    (base_price_override IS NULL OR base_price_override >= 0)
    AND (included_image_count_override IS NULL OR included_image_count_override BETWEEN 0 AND 500)
    AND (included_video_count_override IS NULL OR included_video_count_override BETWEEN 0 AND 500));

-- 3. Pricing policy rules: MEDIA category, profile, component, allowance, rounding --------------------------------
ALTER TABLE public.pricing_rules
    ADD COLUMN IF NOT EXISTS pricing_profile varchar(60),
    ADD COLUMN IF NOT EXISTS media_component varchar(30),
    ADD COLUMN IF NOT EXISTS included_quantity integer,
    ADD COLUMN IF NOT EXISTS rounding_mode varchar(20);

-- Hibernate enum CHECKs list the old values; replace them so MEDIA / new enums are accepted.
DO $$ DECLARE n text; BEGIN
    FOR n IN SELECT conname FROM pg_constraint WHERE conrelid = 'public.pricing_rules'::regclass AND contype = 'c'
        AND (pg_get_constraintdef(oid) LIKE '%MANAGER_ADJUSTMENT%' OR pg_get_constraintdef(oid) LIKE '%media_component%'
             OR pg_get_constraintdef(oid) LIKE '%rounding_mode%') LOOP
        EXECUTE format('ALTER TABLE public.pricing_rules DROP CONSTRAINT %I', n);
    END LOOP;
    FOR n IN SELECT conname FROM pg_constraint WHERE conrelid = 'public.quote_items'::regclass AND contype = 'c'
        AND pg_get_constraintdef(oid) LIKE '%MANAGER_ADJUSTMENT%' LOOP
        EXECUTE format('ALTER TABLE public.quote_items DROP CONSTRAINT %I', n);
    END LOOP;
END $$;

ALTER TABLE public.pricing_rules DROP CONSTRAINT IF EXISTS pricing_rules_category_check;
ALTER TABLE public.pricing_rules ADD CONSTRAINT pricing_rules_category_check CHECK (category IN
    ('PACKAGE','DISTANCE','AREA','CHECKLIST','OUTPUT','RESOURCE','TERRAIN','CONDITION','PERMIT','DISCOUNT','MANAGER_ADJUSTMENT','MEDIA'));
ALTER TABLE public.pricing_rules DROP CONSTRAINT IF EXISTS pricing_rules_media_component_check;
ALTER TABLE public.pricing_rules ADD CONSTRAINT pricing_rules_media_component_check CHECK (media_component IS NULL OR media_component IN
    ('CHECKLIST_BASE','INCLUDED_IMAGE','INCLUDED_VIDEO','EXTRA_IMAGE','EXTRA_VIDEO'));
ALTER TABLE public.pricing_rules DROP CONSTRAINT IF EXISTS pricing_rules_rounding_mode_check;
ALTER TABLE public.pricing_rules ADD CONSTRAINT pricing_rules_rounding_mode_check CHECK (rounding_mode IS NULL OR rounding_mode IN
    ('CEILING','FLOOR','HALF_UP'));
ALTER TABLE public.pricing_rules DROP CONSTRAINT IF EXISTS ck_pricing_rule_media;
ALTER TABLE public.pricing_rules ADD CONSTRAINT ck_pricing_rule_media CHECK (
    (category = 'MEDIA' AND pricing_profile IS NOT NULL AND media_component IS NOT NULL AND fixed_amount IS NOT NULL AND fixed_amount >= 0
        AND (included_quantity IS NULL OR included_quantity BETWEEN 0 AND 500)
        AND (media_component NOT IN ('EXTRA_IMAGE','EXTRA_VIDEO') OR (unit_size >= 1 AND COALESCE(min_value,0) >= 0
            AND (max_value IS NULL OR max_value > COALESCE(min_value,0)))))
    OR (category <> 'MEDIA' AND pricing_profile IS NULL AND media_component IS NULL AND included_quantity IS NULL AND rounding_mode IS NULL));

ALTER TABLE public.quote_items DROP CONSTRAINT IF EXISTS quote_items_type_check;
ALTER TABLE public.quote_items ADD CONSTRAINT quote_items_type_check CHECK (type IN
    ('PACKAGE','DISTANCE','AREA','CHECKLIST','OUTPUT','RESOURCE','TERRAIN','CONDITION','PERMIT','DISCOUNT',
     'MANAGER_ADJUSTMENT','INCLUDED','ADDITIONAL','ADJUSTMENT','MEDIA'));

-- 4. Order checklist snapshot: frozen pricing inputs + evidence policy V3 -----------------------------------------
ALTER TABLE public.order_checklist_items
    ADD COLUMN IF NOT EXISTS minimum_image_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS minimum_video_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pricing_snapshot_version integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS base_price_snapshot numeric(19,0) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS included_image_count_snapshot integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS included_video_count_snapshot integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS requested_image_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS requested_video_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS standard_video_duration_snapshot integer,
    ADD COLUMN IF NOT EXISTS pricing_profile_snapshot varchar(60),
    ADD COLUMN IF NOT EXISTS manager_quote_required boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS manager_quoted_price numeric(19,0);

-- Only a checklist the policy could not price may carry the Manager's own price.
ALTER TABLE public.order_checklist_items DROP CONSTRAINT IF EXISTS ck_order_checklist_manager_quote;
ALTER TABLE public.order_checklist_items ADD CONSTRAINT ck_order_checklist_manager_quote CHECK (
    manager_quoted_price IS NULL OR (manager_quote_required AND manager_quoted_price >= 0));

ALTER TABLE public.order_checklist_items DROP CONSTRAINT IF EXISTS ck_order_checklist_pricing_snapshot;
ALTER TABLE public.order_checklist_items ADD CONSTRAINT ck_order_checklist_pricing_snapshot CHECK (
    pricing_snapshot_version IN (0, 1) AND base_price_snapshot >= 0
    AND included_image_count_snapshot BETWEEN 0 AND 500 AND included_video_count_snapshot BETWEEN 0 AND 500
    AND requested_image_count BETWEEN 0 AND 500 AND requested_video_count BETWEEN 0 AND 500
    AND (pricing_snapshot_version = 1 OR (base_price_snapshot = 0 AND included_image_count_snapshot = 0
        AND included_video_count_snapshot = 0 AND requested_image_count = 0 AND requested_video_count = 0)));

-- Legacy and V1/V2 snapshots must keep zero per-type thresholds; only V3 uses them.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM public.order_checklist_items WHERE evidence_policy_version NOT IN (0,1,2,3)) THEN
        RAISE EXCEPTION 'Unsupported order checklist evidence snapshot; inspect rows before migration';
    END IF;
END $$;

ALTER TABLE public.order_checklist_items DROP CONSTRAINT IF EXISTS ck_snapshot_evidence_policy;
ALTER TABLE public.order_checklist_items ADD CONSTRAINT ck_snapshot_evidence_policy CHECK (
    (evidence_policy_version = 0 AND minimum_evidence_count = 0 AND minimum_image_count = 0 AND minimum_video_count = 0)
    OR (evidence_policy_version = 1 AND minimum_evidence_count = 1 AND required_evidence_type = 'IMAGE_OR_VIDEO'
        AND minimum_image_count = 0 AND minimum_video_count = 0)
    OR (evidence_policy_version = 2 AND minimum_evidence_count BETWEEN 1 AND 50
        AND required_evidence_type IN ('IMAGE','VIDEO','IMAGE_OR_VIDEO') AND minimum_image_count = 0 AND minimum_video_count = 0)
    OR (evidence_policy_version = 3 AND minimum_image_count >= 0 AND minimum_video_count >= 0
        AND minimum_evidence_count = minimum_image_count + minimum_video_count AND minimum_evidence_count BETWEEN 1 AND 1000
        AND required_evidence_type IN ('IMAGE','VIDEO','IMAGE_AND_VIDEO','IMAGE_OR_VIDEO')));

COMMIT;
