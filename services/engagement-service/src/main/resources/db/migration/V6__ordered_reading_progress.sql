CREATE UNIQUE INDEX uq_reading_session_event ON reading_session_entries(event_id) WHERE event_id IS NOT NULL;
CREATE TABLE reading_progress_offsets (aggregate_id UUID PRIMARY KEY, last_revision BIGINT NOT NULL DEFAULT -1);

