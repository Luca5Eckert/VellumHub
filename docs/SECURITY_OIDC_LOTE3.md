# SEC-07 / SEC-08 — Lote 3: Google OIDC and Auth sessions

**State:** proposed code in [draft PR #334](https://github.com/Luca5Eckert/VellumHub/pull/334); not merged, deployed or cut over. This runbook documents the opt-in implementation and its unverified acceptance gates.

## Ownership and protocol

- Google is an upstream OIDC provider only. Spring Security validates its ID Token, issuer/audience/time and OAuth callback; Auth resolves the verified `(issuer, subject)` to one VellumHub UUIDv7 and issues its own RS256 `at+jwt` for the Gateway and APIs.
- `auth_db` owns the new `auth_external_identity` binding, password credentials and JDBC authorization/session state. `user_db` retains profile data and the old Google implementation only while the #317 legacy cutover remains outstanding.
- A new Google subject creates one passwordless Auth identity in `PENDING_PROFILE`; Auth provisions User via the existing idempotent internal endpoint, then marks the Auth identity ACTIVE. A failed commit/timeout retains the pending record for reconciliation. No distributed transaction is claimed.
- An existing email without an existing linked Google subject is rejected. No automatic account linking is ever performed by email. Manual authenticated linking is a **remaining acceptance gap** in #308.
- SPA uses Authorization Code + PKCE S256, public client, no secret or refresh token, access token held only in memory. Do not reuse upstream Google ID tokens as API bearers.

## Prerequisites and local startup

Create a Google OAuth client of type Web application and register the exact authorized redirect URI:
`http://127.0.0.1:9000/login/oauth2/code/google` (loopback development only). The browser can access localhost; the server must also reach Google's token and JWKS endpoints. Supply secrets outside the repository:

```sh
export VELLUM_GOOGLE_CLIENT_ID='<Google OIDC client ID>'
export VELLUM_GOOGLE_CLIENT_SECRET='<Google OIDC client secret>'
export VELLUM_AUTH_DB_PASSWORD='<local DB password>'
export VELLUM_INTERNAL_PROVISION_KEY='<random 32+ character provision key>'
export VELLUM_POC_RUN_AS="$(id -u):$(id -g)"
export VELLUM_POC_LOGIN='fixture@example.invalid'
export VELLUM_POC_PASSWORD='<unique local fixture password>'
sh infra/scripts/security/generate_auth_poc_keys.sh
docker compose -f docker-compose.yml -f docker-compose.oidc-lote2.yml -f docker-compose.oidc-lote3.yml --profile oidc-lote2 up --build
```

Never use local HTTP or PoC fixture settings on a shared or public host. Behind HTTPS, validate the trusted forwarded-header setup, issuer and callback equality, cookie Secure/HttpOnly/SameSite policies, CORS allowlist and secrets mounts. Production needs authenticated inter-service transport, DB encryption/backup/rotation, login throttling and incident handling.

## Session lifecycle

- Spring Session JDBC stores browser sessions in `auth_db`. Schema is Flyway-owned; Spring Session automatic DDL is disabled.
- Inactivity: 30 minutes (`spring.session.timeout`). Absolute age: 12 hours (configurable `VELLUM_AUTH_SESSION_ABSOLUTE_SECONDS`). Independent per-device sessions allowed; logout targets only the active browser session.
- The AS exposes OIDC RP-initiated logout at `/connect/logout` and form logout via **CSRF-protected POST** `/logout`. Registered `post_logout_redirect_uri` is allowlisted per client. Verify OIDC logout state/id_token_hint/client binding and CSRF before use.
- When an AS session is still valid, the SPA can initiate another top-level authorization redirect/PKCE S256 after a token expires; no silent iframe flow or refresh token.
- Local logout does **not** sign the user out of Google. A preexisting Google SSO session can authenticate them again. Resource Servers do not introspect JWT per request: an issued JWT remains accepted through `exp` even after logout; default issuance TTL is 10 minutes.

## Verification gates (must run before marking ready to merge)

1. `mvn -B -ntp -Pauth-poc -pl services/auth-service -am clean verify` including Flyway PostgreSQL/Testcontainers, canonical-subject and session policy unit tests.
2. Compose overlay config with dummy external credentials, plus real runtime using explicitly provided development Google client credentials.
3. Provider-backed browser E2E: new subject → pending identity → same UUID profile → Auth OIDC token → Gateway → two independent protected APIs, and existing subject retains UUID after repeat login.
4. Negative OIDC: invalid `state`/`nonce`, wrong issuer/audience/redirect, unverified email, concurrent first login, duplicate external binding, same email/different subject and Google ID Token directly in Gateway.
5. Session: form/CSRF and OIDC logout callbacks, cookies and redirects, idle and absolute expiry, multi-instance/restart behavior, fresh authorization after logout, and pre-logout JWT accepted only until `exp`.
6. Regression: old User Google/login preserved, no User/Google network fan-out on JWT verification, JWKS warm-cache behavior under Auth outage, no credentials/tokens/cookies in logs.
7. Security review, replay/conflict evidence, SHA-linked CI run, updated Vault and final acceptance reconciliation for #308/#309.

## Rollback and known limits

Disable the `google-oidc` profile and drop the Lote 3 Compose overlay; keep the Lote 2/legacy route available. Preserve auth_db migrations, session and identity data for audit; don't destructively roll back schema or overwrite claims. No deploy or cutover is claimed.

Remaining gaps: authenticated local→Google manual linking and proof of possession, live Google-provider E2E, multi-instance and production HTTPS behavior. A successful unit/Compose CI check is **not** proof of these guarantees. Keep PR #334 draft until verified.
