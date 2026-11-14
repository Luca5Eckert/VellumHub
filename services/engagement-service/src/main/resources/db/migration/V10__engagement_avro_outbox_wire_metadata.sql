-- Additive metadata: existing JSON rows keep their payload, eventId, revision and destination.
ALTER TABLE outbox_events
    ADD COLUMN wire_format VARCHAR(8) NOT NULL DEFAULT 'JSON',
    ADD COLUMN payload_schema_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE outbox_events
    ADD CONSTRAINT ck_engagement_wire_format CHECK(wire_format IN ('JSON','AVRO')),
    ADD CONSTRAINT ck_engagement_payload_version CHECK(payload_schema_version >= 1),
    ADD CONSTRAINT ck_engagement_avro_topic CHECK (
        wire_format <> 'AVRO' OR
        (aggregate_type='RATING' AND topic='engagement.ratings.v1.avro'
          AND event_type IN ('RatingCreatedEvent','RatingUpdatedEvent','RatingDeletedEvent','RatingBaselineEvent'))
        OR (aggregate_type='REACTION' AND topic='engagement.reactions.v1.avro'
          AND event_type IN ('ReactionChangedEvent','ReactionBaselineEvent'))
    );
COMMENT ON COLUMN outbox_events.payload_schema_version IS
    'Version of persisted JSONB, not Registry schema ID or aggregate revision';
COMMENT ON COLUMN outbox_events.wire_format IS
    'Immutable wire destination selected when creating the business event';

-- Two independent writer fences permit separate cutover of Ratings and Reactions.
CREATE TABLE engagement_write_gate (
    aggregate_type VARCHAR(20) PRIMARY KEY CHECK (aggregate_type IN ('RATING','REACTION')),
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO engagement_write_gate(aggregate_type,frozen)
VALUES('RATING',FALSE),('REACTION',FALSE);
