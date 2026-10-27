-- SEC-16: Auth owns the minimum human authority model before legacy User JWT issuance is removed.
ALTER TABLE auth_identity
    ADD COLUMN role varchar(32) NOT NULL DEFAULT 'USER';

ALTER TABLE auth_identity
    ADD CONSTRAINT ck_auth_identity_role CHECK (role IN ('USER', 'ADMIN'));
