-- SEC-07: Auth owns external provider login bindings; User keeps only legacy compatibility.
-- A federated-only user cannot form-login and is blocked until User profile provisioning succeeds.
ALTER TABLE auth_identity ALTER COLUMN password_hash DROP NOT NULL;
CREATE TABLE auth_external_identity (
    issuer varchar(255) NOT NULL,
    subject varchar(255) NOT NULL,
    user_id uuid NOT NULL REFERENCES auth_identity(id) ON DELETE RESTRICT,
    linked_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_auth_external_identity PRIMARY KEY (issuer, subject),
    CONSTRAINT uq_auth_external_user_issuer UNIQUE (user_id, issuer),
    CONSTRAINT ck_auth_external_subject CHECK (length(trim(subject)) > 0)
);
CREATE INDEX idx_auth_external_identity_user ON auth_external_identity(user_id);
