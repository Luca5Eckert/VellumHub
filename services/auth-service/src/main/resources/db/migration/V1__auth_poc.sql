-- SEC-05: isolated Authorization Server schema. Do not share user_db or migrate live identities.
-- Official Spring Security 7 JDBC schema adapted to PostgreSQL (TIMESTAMPTZ and TEXT).
CREATE TABLE oauth2_registered_client (
  id varchar(100) PRIMARY KEY,
  client_id varchar(100) NOT NULL UNIQUE,
  client_id_issued_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  client_secret varchar(200),
  client_secret_expires_at timestamptz,
  client_name varchar(200) NOT NULL,
  client_authentication_methods varchar(1000) NOT NULL,
  authorization_grant_types varchar(1000) NOT NULL,
  redirect_uris varchar(1000),
  post_logout_redirect_uris varchar(1000),
  scopes varchar(1000) NOT NULL,
  client_settings varchar(2000) NOT NULL,
  token_settings varchar(2000) NOT NULL
);
CREATE TABLE oauth2_authorization (
  id varchar(100) PRIMARY KEY,
  registered_client_id varchar(100) NOT NULL,
  principal_name varchar(200) NOT NULL,
  authorization_grant_type varchar(100) NOT NULL,
  authorized_scopes varchar(1000),
  attributes text,
  state varchar(500),
  authorization_code_value text,
  authorization_code_issued_at timestamptz,
  authorization_code_expires_at timestamptz,
  authorization_code_metadata text,
  access_token_value text,
  access_token_issued_at timestamptz,
  access_token_expires_at timestamptz,
  access_token_metadata text,
  access_token_type varchar(100),
  access_token_scopes varchar(1000),
  oidc_id_token_value text,
  oidc_id_token_issued_at timestamptz,
  oidc_id_token_expires_at timestamptz,
  oidc_id_token_metadata text,
  refresh_token_value text,
  refresh_token_issued_at timestamptz,
  refresh_token_expires_at timestamptz,
  refresh_token_metadata text,
  user_code_value text,
  user_code_issued_at timestamptz,
  user_code_expires_at timestamptz,
  user_code_metadata text,
  device_code_value text,
  device_code_issued_at timestamptz,
  device_code_expires_at timestamptz,
  device_code_metadata text,
  CONSTRAINT fk_oauth2_authorization_client FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client(id)
);
CREATE INDEX idx_authorization_principal ON oauth2_authorization(principal_name);
CREATE INDEX idx_authorization_state ON oauth2_authorization(state);
CREATE TABLE oauth2_authorization_consent (
  registered_client_id varchar(100) NOT NULL,
  principal_name varchar(200) NOT NULL,
  authorities varchar(1000) NOT NULL,
  PRIMARY KEY (registered_client_id, principal_name),
  CONSTRAINT fk_consent_client FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client(id)
);
CREATE TABLE auth_identity (
  id uuid PRIMARY KEY,
  login varchar(320) NOT NULL,
  password_hash varchar(255) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_auth_identity_login_case_insensitive ON auth_identity (lower(login));
