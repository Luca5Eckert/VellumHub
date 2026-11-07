-- Preserve legacy JSONB and its immutable publication route across upgrades and rollbacks.
ALTER TABLE outbox_events ADD COLUMN wire_format VARCHAR(12) NOT NULL DEFAULT 'JSON';
ALTER TABLE outbox_events ADD COLUMN payload_schema_version SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_wire_format
    CHECK (wire_format IN ('JSON', 'AVRO'));
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_payload_version
    CHECK (payload_schema_version = 1);
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_avro_book_only
    CHECK (wire_format <> 'AVRO' OR
           (aggregate_type = 'BOOK' AND event_type = 'BookChangedEvent' AND topic = 'book-events.avro'));
-- Outbox rows from all previous deployments remain JSON and are never rewritten.
