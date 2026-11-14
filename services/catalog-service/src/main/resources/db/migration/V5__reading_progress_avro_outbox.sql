-- Extend the existing immutable per-row routing without modifying JSONB or old records.
ALTER TABLE outbox_events DROP CONSTRAINT ck_outbox_avro_book_only;
ALTER TABLE outbox_events ADD CONSTRAINT ck_outbox_avro_contract_route CHECK (
    wire_format <> 'AVRO'
    OR (aggregate_type='BOOK' AND event_type='BookChangedEvent' AND topic='book-events.avro')
    OR (aggregate_type='BOOK_PROGRESS' AND event_type='ReadingProgressChangedEvent'
        AND topic='reading-progress-events.avro')
);

-- UPDATE frozen waits for writers holding SELECT FOR SHARE in their mutation transaction.
CREATE TABLE reading_progress_write_gate (
    id SMALLINT PRIMARY KEY CHECK (id=1),
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO reading_progress_write_gate(id,frozen) VALUES (1,FALSE);
