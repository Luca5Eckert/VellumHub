-- Legacy entries deliberately retain unknown occurrence metadata.
ALTER TABLE reading_session_entries ADD COLUMN event_id UUID;
CREATE INDEX idx_reading_session_entries_event_id ON reading_session_entries (event_id);
CREATE INDEX idx_reading_session_entries_user_book_timestamp
    ON reading_session_entries (user_id, book_snapshot_book_id, timestamp);
