CREATE TABLE preference_event_offsets (
    user_id UUID PRIMARY KEY,
    last_revision BIGINT NOT NULL CHECK (last_revision >= -1)
);

CREATE TABLE processed_preference_events (
    user_id UUID NOT NULL REFERENCES preference_event_offsets(user_id),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    event_id UUID NOT NULL UNIQUE,
    PRIMARY KEY (user_id, revision)
);
