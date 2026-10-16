# Vietnam airspace restrictions

ODMS stores authorized aviation restrictions separately from simulation zones. No regulatory polygons are bundled, scraped, or inferred. Until an administrator imports a valid authorized dataset, validation returns `DATA_UNAVAILABLE` and requires manual review.

## Database setup

Apply `src/main/resources/seeddata/migrations/add_airspace_restrictions.sql` after the existing schema. It creates `airspace_zones` with a GIST geometry index, synchronization history, manager verification records, and optional WGS84 columns for planned waypoints.

## Authorized import

An administrator uploads a UTF-8 GeoJSON `FeatureCollection`:

`POST /api/airspace/admin/import` as multipart fields `file`, `sourceName`, and optional `sourceVersion`.

Every feature must be a valid WGS84 Polygon or MultiPolygon with unique `externalId`, `name`, and `zoneType` (`PROHIBITED` or `RESTRICTED`). Optional properties are `minimumAltitudeM`, `maximumAltitudeM`, `validFrom`, `validUntil`, and `restrictionsDescription`. The full file is validated before any zone is written. Existing `(sourceName, externalId)` records are updated. Empty, invalid, oversized, or partially corrupt datasets are rejected without replacing valid data.

The provider deliberately reads an explicitly supplied authorized file only. It does not call or scrape `cambay.mod.gov.vn`.

## APIs

- `GET /api/airspace/zones?bbox=west,south,east,north&zoom=12` returns a viewport-filtered GeoJSON FeatureCollection with display-only simplification.
- `POST /api/airspace/check` validates a supplied Polygon/MultiPolygon or an order area.
- `POST /api/airspace/check/mission/{missionId}` validates WGS84 plan waypoints with the configured safety buffer. Simulation-only waypoints return `REQUIRES_MANUAL_REVIEW`.
- `GET /api/airspace/export/order/{orderId}` downloads GeoJSON for manual verification.
- `GET|PUT /api/airspace/verification/order/{orderId}` reads/records the manager's internal review.

`NO_KNOWN_INTERSECTION` is not legal authorization or a government flight permit.

## Configuration

- `AIRSPACE_STALE_AFTER_DAYS` (default 30)
- `AIRSPACE_ROUTE_SAFETY_BUFFER_METERS` (default 30)
- `AIRSPACE_MAX_UPLOAD_BYTES` (default 10 MiB)

## Frontend

`AirspaceRestrictionLayer` requests only the visible viewport, debounces map moves, draws prohibited areas red and restricted areas orange, and shows source metadata. Customer Create Order runs a compact area check and links to the official portal. Manager Order Detail supports GeoJSON download and records review status/notes.
# Demo mode

Set `SPRING_PROFILES_ACTIVE=demo` (or `dev`) before starting the backend. The
`DemoAirspaceSeedDataInitializer` idempotently creates 20 WGS84 simulation zones.
Every record has `source_type=DEMO`, `is_official=false`, and a name beginning
with `[DEMO]`.

Demo records are returned and used for intersection checks only while the
`demo` or `dev` Spring profile is active. In other profiles, API/PostGIS queries
explicitly filter them out even if the rows remain in the database. Demo checks
always keep official verification at `REQUIRES_REVIEW` and never authorize a
flight.

For a quick intersection demonstration, use approximately
`10.8440706, 106.4938467` with a 300 m monitoring radius. Restart the backend
after changing profiles, then reload the Create Order map.

