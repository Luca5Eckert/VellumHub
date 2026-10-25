-- SEC-06: User owns profile data, not credentials of new OIDC users.
-- Keep legacy passwords nullable only for accounts provisioned by Auth.
ALTER TABLE tb_users ALTER COLUMN password DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profile_login_casefold ON tb_users (lower(email));
