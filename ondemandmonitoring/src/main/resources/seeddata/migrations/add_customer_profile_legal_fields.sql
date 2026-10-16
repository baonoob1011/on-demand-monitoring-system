-- Adds the legal identifiers required for a complete customer flight-service profile.
-- Safe to run repeatedly on an existing database before starting the application.
ALTER TABLE customer_profiles
    ADD COLUMN IF NOT EXISTS citizen_identification_number VARCHAR(12),
    ADD COLUMN IF NOT EXISTS company_tax_code VARCHAR(14);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_profiles_citizen_identification_number
    ON customer_profiles (citizen_identification_number)
    WHERE citizen_identification_number IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_profiles_company_tax_code
    ON customer_profiles (company_tax_code)
    WHERE company_tax_code IS NOT NULL;
