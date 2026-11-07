-- Existing Book / Reading Progress rows are always legacy JSON on their original topic.
-- This changes metadata only, never existing payload bytes, identities or aggregate versions.
ALTER TABLE outbox_events
    ADD COLUMN wire_format VARCHAR(8) NOT NULL DEFAULT 'JSON',
    ADD COLUMN payload_schema_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_wire_format CHECK (wire_format IN ('JSON', 'AVRO')),
    ADD CONSTRAINT ck_outbox_payload_schema_version CHECK (payload_schema_version >= 1),
    ADD CONSTRAINT ck_outbox_avro_book_only CHECK
      (wire_format <> 'AVRO' OR
       (aggregate_type = 'BOOK' AND event_type = 'BookChangedEvent'
        AND topic = 'book-events.avro'));

COMMENT ON COLUMN outbox_events.payload_schema_version IS
    'Version of persisted JSONB structure, not Book aggregate revision or Registry schema ID';
COMMENT ON COLUMN outbox_events.wire_format IS
    'Immutable Kafka wire destination selected at enqueue time';

-- A single database-owned cutover fence synchronizes all Catalog replicas.
-- UPDATE book_write_gate SET frozen=true WHERE id=1 waits for transactions
-- holding SELECT FOR SHARE; relays keep running while writes are frozen.
CREATE TABLE book_write_gate (
    id SMALLINT PRIMARY KEY CHECK (id=1),
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO book_write_gate(id, frozen) VALUES (1, FALSE);
