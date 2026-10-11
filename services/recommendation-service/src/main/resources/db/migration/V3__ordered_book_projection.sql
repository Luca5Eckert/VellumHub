-- Per-book checkpoint for atomic projections and read-model schema matching Catalog.
CREATE TABLE book_event_offsets (
    book_id UUID PRIMARY KEY,
    last_revision BIGINT NOT NULL DEFAULT -1
);
ALTER TABLE recommendations ALTER COLUMN description TYPE VARCHAR(1000);
ALTER TABLE recommendations ALTER COLUMN description DROP NOT NULL;
ALTER TABLE recommendations ALTER COLUMN cover_url DROP NOT NULL;

