CREATE TABLE processed_events (event_id UUID PRIMARY KEY, processed_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE reading_progress_offsets (aggregate_id UUID PRIMARY KEY, last_revision BIGINT NOT NULL DEFAULT -1);


-- An optimistic lock on UserProfile prevents lost vector/score updates across different books.
ALTER TABLE user_profiles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

