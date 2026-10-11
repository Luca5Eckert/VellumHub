-- Durable per-book offset; effects and checkpoints share the consumer's transaction.
CREATE TABLE book_event_offsets (
    book_id UUID PRIMARY KEY,
    last_revision BIGINT NOT NULL DEFAULT -1
);


-- Preserve historic rating/reaction/reading_session_entry foreign keys on deletion.
ALTER TABLE book_snapshot ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
