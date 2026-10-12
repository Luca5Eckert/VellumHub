-- Idempotency and ordering fences for incremental user-profile adjustments.
-- Two independent bounded-context streams may run concurrently; UserProfile @Version
-- protects cross-stream lost updates.
CREATE TABLE processed_engagement_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(160) NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_processed_engagement_aggregate
  ON processed_engagement_events(aggregate_type,aggregate_id,processed_at);

CREATE TABLE engagement_event_offsets (
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    last_revision BIGINT NOT NULL DEFAULT -1,
    PRIMARY KEY(aggregate_type,aggregate_id)
);

