ALTER TABLE reading_session_entries ADD COLUMN page_delta INTEGER;

CREATE INDEX idx_reading_session_diary ON reading_session_entries
    (user_id, reading_session_id, (COALESCE(read_at, timestamp)) DESC)
    WHERE COALESCE(read_at, timestamp) IS NOT NULL;

CREATE TABLE reading_note (
    id UUID PRIMARY KEY,
    reading_id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR(5) NOT NULL CHECK (type IN ('NOTE', 'QUOTE')),
    page INTEGER CHECK (page >= 0),
    note_text VARCHAR(2000) NOT NULL CHECK (length(btrim(note_text)) > 0),
    read_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_reading_note_diary ON reading_note (user_id, reading_id, read_at DESC, id DESC);
