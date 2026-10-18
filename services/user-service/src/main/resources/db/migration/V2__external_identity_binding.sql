-- SEC-02: a verified Google identity is keyed by issuer + subject, never by email.
-- Existing users are intentionally NOT backfilled by email.
-- Null passwords are valid only for externally provisioned identities.
ALTER TABLE tb_users ALTER COLUMN password DROP NOT NULL;

CREATE TABLE external_identities (
    id UUID PRIMARY KEY,
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL REFERENCES tb_users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_external_identity_provider_subject UNIQUE (issuer, subject),
    CONSTRAINT uk_external_identity_user_provider UNIQUE (user_id, issuer),
    CONSTRAINT ck_external_identity_subject_nonblank CHECK (length(trim(subject)) > 0)
);

CREATE TABLE external_identity_audit (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES tb_users(id) ON DELETE RESTRICT,
    issuer VARCHAR(255) NOT NULL,
    subject_sha256 CHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_external_identity_audit_action CHECK (action IN ('CREATED', 'LINKED', 'UNLINKED'))
);
CREATE INDEX idx_external_identity_audit_user_time
    ON external_identity_audit (user_id, recorded_at DESC);
