-- Run on an existing PostgreSQL/PostGIS database before setting JPA_DDL_AUTO=validate.
-- For fresh local databases, Hibernate may create depots from the Depot entity.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS public.depots (
    id varchar(255) PRIMARY KEY,
    name varchar(150) NOT NULL,
    address varchar(255),
    location geometry(Point,4326) NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL,
    updated_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT depots_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_depots_location
    ON public.depots USING gist (location);
CREATE INDEX IF NOT EXISTS idx_depots_status
    ON public.depots (status);
