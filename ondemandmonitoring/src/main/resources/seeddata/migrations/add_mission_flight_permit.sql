ALTER TABLE public.missions
    ADD COLUMN IF NOT EXISTS flight_permit_status varchar(30) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN IF NOT EXISTS flight_permit_note varchar(1000),
    ADD COLUMN IF NOT EXISTS flight_permit_file_name varchar(160),
    ADD COLUMN IF NOT EXISTS flight_permit_content_type varchar(100),
    ADD COLUMN IF NOT EXISTS flight_permit_data_url text;
