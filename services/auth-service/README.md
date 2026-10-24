# SEC-05 — Spring Security 7 Authorization Server PoC

**Status: implemented in branch only; not a production cutover.** This independent experimental service is opt-in and does not replace the existing User Service JWT issuer. Tracks [#306](https://github.com/Luca5Eckert/VellumHub/issues/306) and [ADR-015](https://github.com/Luca5Eckert/obsidian-vault/blob/main/projects/vellum-hub/architecture/adr/ADR-015-identidade-e-authorization-server.md).

## Boundaries

- Identity, password hash, OAuth registered-client/authorization/consent tables: **auth_db** (new PostgreSQL).
- New human identity uses RFC 9562 UUIDv7 as `sub`; email is only a login field.
- SPA client `vellumhub-spa-poc` is **public**, `authorization_code` only, PKCE S256 mandatory, no refresh grant, no client secret.
- JWT access tokens are RS256 and use `typ=at+jwt`, `aud=api://vellumhub`, UUID subject, `client_id`, `jti`, and stable `kid`; OIDC ID Tokens remain distinct.
- PoC protected API `/poc/api/me` validates the bearer independently using trusted public JWKS and the existing `lib/jwt-security` profile.
- Auth DB is **not** user_db, and none of the existing five services changes its default issuer. Google federation, production onboarding, authorization policy and legacy cutover remain #307/#308/#309/#310/#317.

## Launch (local loopback only)

Prerequisites: Docker Compose, openssl, Python 3 and Java 21/Maven for JVM tests. No passwords or private keys are checked in.

```sh
sh infra/scripts/security/generate_auth_poc_keys.sh
# The container process must have the exact numeric owner UID:GID of the protected 0600 PEMs.
export VELLUM_POC_RUN_AS="$(id -u):$(id -g)"
export VELLUM_AUTH_DB_PASSWORD='replace-with-strong-local-db-password'
export VELLUM_POC_LOGIN='test@example.invalid'
export VELLUM_POC_PASSWORD='replace-with-unique-strong-test-password'
docker compose -f docker-compose.auth-poc.yml --profile auth-poc up --build
# another terminal:
python3 -m http.server 9100 --bind 127.0.0.1 --directory infra/auth-poc/spa
sh infra/scripts/security/verify_auth_poc.sh
# automated browser-like login test (uses configured VELLUM_POC_LOGIN/PASSWORD):
python3 infra/scripts/security/verify_auth_poc_flow.py
```

Visit `http://127.0.0.1:9100/index.html`, click **Sign in**, enter the configured test credentials, then **Call protected test API**. The authorization server listens on `127.0.0.1:9000` and exposes `/.well-known/openid-configuration`, `/oauth2/jwks`, `/oauth2/authorize` and `/oauth2/token`. Redirect URI is registered exactly; SPA origin is allowed only for the token endpoint/discovery. The static browser harness demonstrates the protocol and nonce/state but **does not cryptographically verify the ID Token**; it must be replaced with a maintained OIDC library before using a real SPA. This is **not** evidence of a production-ready web client.

To avoid accidentally depending on cookie semantics over plain HTTP, local profile sets `secure=false` on the AS session cookie. Non-local configuration defaults to `secure=true` and requires an explicit HTTPS issuer, trusted secrets, TLS and tested CSRF/CORS policy. Never use the local profile on a shared or public host.

To run tests:

```sh
mvn -B -ntp -Pauth-poc -pl services/auth-service -am verify
```

JUnit integration tests use PostgreSQL Testcontainers and require a working Docker runtime. Gradual rollout and detached Gateway/Resource Server integration are **not** done by this PoC.

## Persistence and secrets

Flyway initializes `oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent` and `auth_identity`. JDBC repositories are used; authorization codes/consents persist across restarts. Client bootstrapping is idempotent and rejects an existing client with a conflicting security policy. Demo credentials are created only under `local-poc`, are BCrypt-encoded at rest, and never overwritten on restart. If you change the login, use a fresh database volume rather than silently overwriting an identity.

The RSA key pair lives in `.local/auth-poc` (gitignored), mounted read-only. The signing service rejects missing, mismatched or weak keys. It **never** autogenerates runtime keys. Rotate keys only with a coordinated overlap/cache/expiry plan; this PoC has one active key. Never mount its private key into other services.

## Security checks and gaps

- Reject authorization codes with invalid/verifier, redirect mismatch, unsupported grant and reused/expired code. Authorization Code + PKCE, session login and CSRF remain Spring Security responsibility.
- Only `openid`/`profile` are permitted in the public PoC client; **no refresh token** and no automatic upgrade to privileged scopes. Access tokens are held in JavaScript memory, not local/sessionStorage.
- The temporary `roles: [USER]` access-token claim represents the fixture's locally authenticated USER role; #311 must implement full policy and conversion.
- `/poc/api/me` is a security **demo**, not a production endpoint. Other services still validate the previous issuer; the PoC does not enable open dual issuer acceptance.
- Logout button clears local token memory only; global session logout/revocation remains #309. A signed access token stays valid until `exp`, even if browser memory is cleared.
- No user migration is required because there are no real users; do not delete the #303 Google link behavior or migrate its persistent records speculatively.
- Failures of signing keys, JDBC and issuer settings should fail startup. Do not log client secrets, credentials, authorization codes, refresh or access tokens.

### Validation evidence

Code is committed as a PoC; **full Maven verify, Docker Compose runtime, PKCE browser E2E, JWKS cache/AS-outage and deployment have not been executed within this assistant environment**. CI results must be checked on the final PR SHA; no success or rollout is claimed without evidence. Follow the [Obsidian execution plan](https://github.com/Luca5Eckert/obsidian-vault/blob/main/projects/vellum-hub/plan/08%20-%20Plano%20SEC-05%20Spring%20Authorization%20Server%20SPA.md).
