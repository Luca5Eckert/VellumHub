-- The Outbox is local to Engagement and intentionally independent of the Catalog Outbox.
-- IDs of Rating and Reaction are BIGINT; their stable textual IDs are used in the envelope.
ALTER TABLE rating ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE reactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE outbox_events (
  event_id UUID PRIMARY KEY,
  aggregate_type VARCHAR(40) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  event_type VARCHAR(100) NOT NULL,
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
  CONSTRAINT ck_engagement_outbox_status CHECK (status IN ('PENDING','IN_FLIGHT','PUBLISHED')),
  CONSTRAINT uq_engagement_outbox_revision UNIQUE(aggregate_type, aggregate_id, aggregate_version)
);
CREATE INDEX idx_engagement_outbox_pending ON outbox_events(next_attempt_at,created_at)
  WHERE status='PENDING';
CREATE INDEX idx_engagement_outbox_leased ON outbox_events(lease_until)
  WHERE status='IN_FLIGHT';
CREATE INDEX idx_engagement_outbox_sequence ON outbox_events(aggregate_type,aggregate_id,aggregate_version)
  WHERE status <> 'PUBLISHED';

