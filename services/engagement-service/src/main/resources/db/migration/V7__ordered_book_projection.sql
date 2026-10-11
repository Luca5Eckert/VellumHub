-- Durable per-book offset; effects and checkpoints share the consumer's transaction.
CREATE TABLE book_event_offsets (
    book_id UUID PRIMARY KEY,
    last_revision BIGINT NOT NULL DEFAULT -1
);

