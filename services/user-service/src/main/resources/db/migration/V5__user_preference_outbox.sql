ALTER TABLE user_preferences ADD COLUMN revision BIGINT NOT NULL DEFAULT -1;

CREATE TABLE user_preference_outbox (
    event_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    genres VARCHAR(255) ARRAY NOT NULL,
    about VARCHAR(255),
    CONSTRAINT uq_user_preference_outbox_revision UNIQUE (user_id, revision)
);

CREATE INDEX idx_user_preference_outbox_ready ON user_preference_outbox (next_attempt_at, event_id);
