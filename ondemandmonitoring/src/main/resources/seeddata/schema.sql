CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE IF EXISTS public.zones
    ADD COLUMN IF NOT EXISTS restricted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS public.atmosphere_profiles (
    id VARCHAR(255) PRIMARY KEY,
    source_world VARCHAR(120) NOT NULL,
    base_pressure_pa DOUBLE PRECISION NOT NULL,
    base_altitude_m DOUBLE PRECISION NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_atmosphere_profiles_active_world
    ON public.atmosphere_profiles (source_world)
    WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS public.environmental_measurements (
    id VARCHAR(255) PRIMARY KEY,
    drone_id VARCHAR(255) REFERENCES public.drones(id),
    drone_code VARCHAR(50),
    measurement_type VARCHAR(40) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(30) NOT NULL,
    altitude_m DOUBLE PRECISION,
    sim_x DOUBLE PRECISION,
    sim_y DOUBLE PRECISION,
    source_world VARCHAR(120) NOT NULL,
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_environmental_measurements_drone_type_time
    ON public.environmental_measurements (drone_code, measurement_type, measured_at DESC);

ALTER TABLE IF EXISTS public.drone_telemetries
    ADD COLUMN IF NOT EXISTS sim_x DOUBLE PRECISION;

ALTER TABLE IF EXISTS public.drone_telemetries
    ADD COLUMN IF NOT EXISTS sim_y DOUBLE PRECISION;

-- Persistent preflight execution history. JPA creates these tables in normal
-- environments; keep the SQL here for deployments that apply seed schema SQL.
CREATE TABLE IF NOT EXISTS public.preflight_checks (
    id VARCHAR(255) PRIMARY KEY,
    mission_id VARCHAR(255) NOT NULL REFERENCES public.missions(id),
    flight_connection_id VARCHAR(255) REFERENCES public.gcs_sessions(id),
    status VARCHAR(20) NOT NULL,
    total_checks INTEGER NOT NULL,
    passed_checks INTEGER NOT NULL DEFAULT 0,
    failed_checks INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

-- Older local databases may already have a legacy preflight_checks table with
-- telemetry columns. Add the execution-history columns without dropping data.
ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS mission_id VARCHAR(255);

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS flight_connection_id VARCHAR(255);

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS total_checks INTEGER;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS passed_checks INTEGER DEFAULT 0;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS failed_checks INTEGER DEFAULT 0;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS public.preflight_checks
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_preflight_mission_created
    ON public.preflight_checks (mission_id, created_at);

CREATE TABLE IF NOT EXISTS public.preflight_check_items (
    id VARCHAR(255) PRIMARY KEY,
    preflight_check_id VARCHAR(255) NOT NULL REFERENCES public.preflight_checks(id) ON DELETE CASCADE,
    check_type VARCHAR(50) NOT NULL,
    check_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    check_level VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    checked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_preflight_item_type UNIQUE (preflight_check_id, check_type)
);

CREATE INDEX IF NOT EXISTS idx_preflight_item_run
    ON public.preflight_check_items (preflight_check_id);



-- Reset seed order target về tọa độ simulation local XY
UPDATE orders
SET point = ST_SetSRID(ST_MakePoint(200.0, -280.0), 4326),
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'ORD-78234';

-- Xóa plan/waypoints cũ để nhìn rõ accept tạo plan mới
DELETE FROM plan_waypoints
WHERE mission_plan_id IN (
    SELECT id FROM mission_plans WHERE mission_id = 'MSN-2024-0891'
);

DELETE FROM mission_plans
WHERE mission_id = 'MSN-2024-0891';

-- Reset mission về đúng trạng thái chờ operator accept
UPDATE missions
SET status = 'WAITING_OPERATOR_ACCEPTANCE',
    started_at = NULL,
    preflight_passed = NULL,
    preflight_checked_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'MSN-2024-0891';

-- Reset operator assignment về PENDING
UPDATE mission_operator_assignments
SET status = 'PENDING',
    is_current = true,
    responded_at = NULL,
    released_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE mission_id = 'MSN-2024-0891';

-- Đảm bảo drone assignment vẫn current
UPDATE mission_drone_assignments
SET status = 'ACTIVE',
    is_current = true,
    updated_at = CURRENT_TIMESTAMP
WHERE mission_id = 'MSN-2024-0891';



DELETE FROM plan_waypoints
WHERE mission_plan_id IN (
    SELECT id FROM mission_plans WHERE mission_id = 'MSN-2024-0891'
);

DELETE FROM mission_plans
WHERE mission_id = 'MSN-2024-0891';

UPDATE orders
SET point = ST_SetSRID(ST_MakePoint(200.0, -280.0), 4326),
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'ORD-78234';

UPDATE missions
SET status = 'WAITING_OPERATOR_ACCEPTANCE',
    started_at = NULL,
    preflight_passed = NULL,
    preflight_checked_at = NULL,
    preflight_retry_count = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'MSN-2024-0891';

UPDATE mission_operator_assignments
SET status = 'PENDING',
    is_current = true,
    responded_at = NULL,
    released_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE mission_id = 'MSN-2024-0891';

DELETE FROM plan_waypoints
WHERE mission_plan_id IN (
    SELECT id FROM mission_plans WHERE mission_id = 'MSN-2024-0891'
);

DELETE FROM mission_plans
WHERE mission_id = 'MSN-2024-0891';

UPDATE orders
SET point = ST_SetSRID(ST_MakePoint(200.0, -280.0), 4326),
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'ORD-78234';

UPDATE missions
SET status = 'WAITING_OPERATOR_ACCEPTANCE',
    started_at = NULL,
    preflight_passed = NULL,
    preflight_checked_at = NULL,
    preflight_retry_count = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'MSN-2024-0891';

UPDATE mission_operator_assignments
SET status = 'PENDING',
    is_current = true,
    responded_at = NULL,
    released_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE mission_id = 'MSN-2024-0891';
