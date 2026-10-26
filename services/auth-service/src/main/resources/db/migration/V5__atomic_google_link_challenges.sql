-- One challenge per browser session; DELETE RETURNING consumes it across replicas.
CREATE TABLE auth_google_link_challenge (
    session_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth_identity(id) ON DELETE CASCADE,
    nonce varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL
);
CREATE INDEX idx_google_link_challenge_expiry ON auth_google_link_challenge(expires_at);
