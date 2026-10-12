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


-- Apply in a writer-frozen cutover, after draining legacy topics. Existing interactions
-- already contributed to UserProfile; these baselines MUST NOT produce new adjustments.
WITH snapshot AS (
  SELECT gen_random_uuid() event_id, now() captured_at, id, user_id, book_id, version
  FROM rating
)
INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,
  event_type,topic,message_key,payload)
SELECT event_id,'RATING',id::text,version,'RatingBaselineEvent',
       'engagement.ratings.v1',user_id::text,
       jsonb_build_object('eventId',event_id,'occurredAt',captured_at,
           'ratingId',id,'userId',user_id,'bookId',book_id,'revision',version)
FROM snapshot;

WITH snapshot AS (
  SELECT gen_random_uuid() event_id, now() captured_at, id,user_id,book_snapshot_id book_id,version
  FROM reactions
)
INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,
  event_type,topic,message_key,payload)
SELECT event_id,'REACTION',id::text,version,'ReactionBaselineEvent',
       'engagement.reactions.v1',user_id::text,
       jsonb_build_object('eventId',event_id,'occurredAt',captured_at,
           'reactionId',id,'userId',user_id,'bookId',book_id,'revision',version)
FROM snapshot;


-- Reject concurrent duplicate ratings: reconcile legacy duplicates before migration.
CREATE UNIQUE INDEX uq_rating_user_book ON rating(user_id,book_id);
