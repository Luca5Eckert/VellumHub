# SEC-05 — Spring Security 7 Authorization Server PoC

## Security migration status — 27 October 2026

The Auth service and OIDC integration are merged and validated in the opt-in integration path. The default Compose/Kubernetes runtime still uses the User Service JWT/JWKS path until #317. There is no real frontend today; #317 is now the backend/data cutover and legacy-auth removal.


**Status: Lote 1 PoC and Lote 2 integration are merged; Lote 3 is in draft PR #334, without production cutover.** This independent experimental service is opt-in and does not replace the existing User Service JWT issuer. Tracks [#306](https://github.com/Luca5Eckert/VellumHub/issues/306) and [ADR-015](https://github.com/Luca5Eckert/obsidian-vault/blob/main/projects/vellum-hub/architecture/adr/ADR-015-identidade-e-authorization-server.md).

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

## SEC-06 registration and durable onboarding (opt-in)

`VELLUM_IDENTITY_REGISTRATION_ENABLED=true` exposes `POST /v1/identity/register`. The request validates email/name/password, stores only a BCrypt-encoded credential in `auth_db`, and generates an RFC 9562 UUIDv7 once. An account starts `PENDING_PROFILE` and **cannot log in**; Auth invokes User's private profile endpoint, then transitions to ACTIVE. Failure returns HTTP 503, leaves the identity pending and schedules idempotent reconciliation with its **original UUID**. Duplicate email returns 409. Auth never makes a synchronous User call for JWT verification. Configuration requires `VELLUM_IDENTITY_USER_URL` plus `VELLUM_INTERNAL_PROVISION_KEY` (32+ random characters). Profile is not to be provisioned via the public Gateway.

This is a development-only integration until TLS, M2M identity, login/registration throttling, backup/recovery and issuer rollout are validated. The PoC fixture remains separately controlled by the local-poc profile and is NOT a production enrollment path.

[Runbook](../../docs/SECURITY_OIDC_LOTE2.md) · [#307](https://github.com/Luca5Eckert/VellumHub/issues/307).

## SEC-07 / SEC-08 — federated Google login and session lifecycle (PR #334 draft)

`application-google-oidc.yml` activates the official Spring Security OAuth2/OIDC client only with profile `google-oidc`. Set `VELLUM_GOOGLE_CLIENT_ID` and `VELLUM_GOOGLE_CLIENT_SECRET` from a secret store; register the exact callback URL `{authBaseUrl}/login/oauth2/code/google` with Google. Use `local-poc,google-oidc` on loopback only, and `VELLUM_IDENTITY_REGISTRATION_ENABLED=true` to activate the existing private Auth→User provisioner. Always provide the private `VELLUM_INTERNAL_PROVISION_KEY` and `VELLUM_IDENTITY_USER_URL`.

The `V3__google_federated_identities.sql` migration gives Auth sole ownership of new provider bindings, keyed by `(issuer,subject)`. No email auto-link, no Google token as API bearer, no password fallback for Google-only accounts. User Service retains legacy bindings exclusively for old API compatibility until #317; there is no new dual-write. Unlinked existing-email identities fail closed unless the user completes the manual linking flow: authenticated local user POSTs `/v1/identity/google/link/challenge` with the current password and CSRF protection, passes the returned random nonce into Google Identity Services, and POSTs the signed nonce-bound Google ID Token to `/v1/identity/google/link`. The Auth server verifies signature, pinned issuer, client audience, nonce and unique binding before attaching the existing canonical UUID. No email auto-link.

`V4__jdbc_http_sessions.sql` introduces Spring Session JDBC persistence through Flyway. Inactive sessions expire after 30 minutes; `VELLUM_AUTH_SESSION_ABSOLUTE_SECONDS` defaults to 43200. The AS retains the OIDC `/connect/logout` endpoint and a CSRF-protected `POST /logout` for per-device logout. Reauthorization requires new top-level Authorization Code + PKCE S256. The initial SPA receives no refresh token, and previously issued JWTs remain valid until expiry.

Verification: `mvn -B -ntp -Pauth-poc -pl services/auth-service -am clean verify`. CI does not replace live Google credentials, HTTPS deployment checks, provider-driven E2E, or multi-instance session validation. The manual linking HTTP paths require a valid local session, a CSRF token and a fresh Google ID token intended for the configured Google client. See [runbook](../../docs/SECURITY_OIDC_LOTE3.md) for the negative cases, rollback and explicit gaps.


### PR #334 review hardening

The Google-enabled context has an explicit primary local JWT decoder. JDBC authorization serialization has a narrow allowlist for the canonical federated principal. V5 consumes manual-link challenges atomically in PostgreSQL across replicas; V6 enforces a shared authentication-attempt budget. AS sessions and new access-token issuance check the local identity's active state. Session absolute-age enforcement runs after Spring Session and before Spring Security. Initial role issuance remains `USER` until the separate RBAC work.

Controlled-provider integration tests exercise signed callbacks, stable UUID, JDBC authorization/session restoration, local-login regression, logout and expiry. Live Google credentials, HTTPS/proxy checks and real-client cutover remain separate validation. With `google-oidc`, the separate private management listener defaults to port 9001; the Compose Lote 3 + observability profiles discover it without publishing that port. Metrics, alerts, configuration limits and incident response are in the runbook.
