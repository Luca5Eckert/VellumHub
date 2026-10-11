ALTER TABLE book_progress ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE outbox_events (
  event_id UUID PRIMARY KEY,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  topic VARCHAR(160) NOT NULL,
  message_key VARCHAR(160) NOT NULL,
  payload JSONB NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  lease_until TIMESTAMPTZ,
  claim_token UUID,
  last_error VARCHAR(1000),
  published_at TIMESTAMPTZ,
  CONSTRAINT uq_outbox_aggregate_revision UNIQUE(aggregate_type, aggregate_id, aggregate_version),
  CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','IN_FLIGHT','PUBLISHED'))
);
CREATE INDEX idx_outbox_claim ON outbox_events (next_attempt_at, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_lease ON outbox_events (lease_until) WHERE status = 'IN_FLIGHT';
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id, aggregate_version)
  WHERE status <> 'PUBLISHED';


CREATE UNIQUE INDEX uq_active_book_progress
  ON book_progress(user_id, book_id) WHERE is_active = true;

-- Cutover safety: existing progress rows already have history in the legacy topics.
-- Snapshot does not invent a reading occurrence and must not apply a new user-profile adjustment.
-- It only establishes revision zero before post-cutover updates (revision >= 1).
WITH snapshots AS (
  SELECT gen_random_uuid() AS event_id, id AS progress_id, user_id, book_id,
         current_page, reading_status::text AS progress, now() AS captured_at
  FROM book_progress
)
INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, aggregate_version,
                          event_type, topic, message_key, payload)
SELECT event_id, 'BOOK_PROGRESS', progress_id, 0,
       'ReadingProgressChangedEvent', 'reading-progress-events', progress_id::text,
       jsonb_build_object(
         'eventId', event_id, 'occurredAt', captured_at,
         'bookProgressId', progress_id, 'userId', user_id, 'bookId', book_id,
         'progress', progress, 'oldPage', current_page, 'newPage', current_page,
         'revision', 0, 'kind', 'BASELINE')
FROM snapshots;

