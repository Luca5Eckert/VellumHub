-- SEC-06: pending registration is NOT eligible for OAuth login.
-- Existing PoC fixtures remain ACTIVE. Auth is the only owner of password hashes.
ALTER TABLE auth_identity
  ADD COLUMN display_name varchar(120),
  ADD COLUMN provisioning_state varchar(24) NOT NULL DEFAULT 'ACTIVE',
  ADD CONSTRAINT ck_auth_identity_provisioning_state
    CHECK (provisioning_state IN ('PENDING_PROFILE','ACTIVE'));
CREATE INDEX idx_auth_identity_pending ON auth_identity (created_at)
  WHERE provisioning_state = 'PENDING_PROFILE';
