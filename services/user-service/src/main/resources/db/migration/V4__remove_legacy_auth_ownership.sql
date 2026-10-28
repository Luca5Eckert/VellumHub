-- SEC-16: User is profile-only. Authentication state is owned by auth-service.
DROP TABLE IF EXISTS external_identity_audit;
DROP TABLE IF EXISTS external_identities;

ALTER TABLE tb_users DROP COLUMN IF EXISTS password;
ALTER TABLE tb_users DROP COLUMN IF EXISTS role;
