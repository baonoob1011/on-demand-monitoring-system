-- Durable ledger of client-journaled upload failure events.
-- Additive and idempotent. Run once per deployment before using
-- POST /api/media/{id}/upload-attempts/{attemptId}/failures with an `eventId`.
--
-- This is deliberately a separate table rather than a reuse of media_storage_event_inbox:
-- that table asserts a bucket and object key, which would falsely claim an object exists for a
-- transfer that never happened.
BEGIN;

CREATE TABLE IF NOT EXISTS public.media_upload_failure_events (
    id uuid PRIMARY KEY,
    event_key varchar(64) NOT NULL,
    mission_id uuid NOT NULL,
    media_id uuid NOT NULL,
    attempt_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    category varchar(40) NOT NULL,
    occurred_at timestamptz NOT NULL,
    failure_code varchar(100),
    failure_message varchar(1000),
    processed_at timestamptz,
    processing_outcome varchar(40),
    created_at timestamptz NOT NULL,
    updated_at timestamptz,
    version bigint NOT NULL,
    CONSTRAINT uk_media_upload_failure_event UNIQUE (event_key),
    CONSTRAINT ck_upload_failure_category CHECK (
        category IN ('TRANSFER_FAILED', 'TRANSFER_ACK_LOST')),
    -- A processed row must record what was decided, so replay is auditable.
    CONSTRAINT ck_upload_failure_outcome CHECK (
        processed_at IS NULL OR processing_outcome IS NOT NULL)
);

-- Replay scans unprocessed rows in occurrence order.
CREATE INDEX IF NOT EXISTS ix_upload_failure_pending
    ON public.media_upload_failure_events (occurred_at)
    WHERE processed_at IS NULL;

-- Reject a category that does not match a recorded event; keeps replays idempotent.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.media_upload_failure_events
               WHERE category NOT IN ('TRANSFER_FAILED','TRANSFER_ACK_LOST')) THEN
        RAISE EXCEPTION 'Unsupported upload failure category present';
    END IF;
END $$;

COMMIT;