## Post-merge status — 27 October 2026

This document preserves the historical SEC-06/09 integration and opt-in harness decisions. SEC-16 / #317 has since promoted auth-service to the standard Compose/Kubernetes runtime and removed User-side authentication; statements below about the *legacy default*, temporary dual topology or unimplemented later batches describe the historical stage, not the current branch. No real frontend or production-user migration exists.

# SEC-06 + SEC-09 / #307 + #310 — Integration rollout and evidence

**Status: branch implementation; do not merge or report production until CI and distributed E2E pass.**

## Invariants
- Auth is the only owner of **new OIDC credentials** and UUIDv7; User owns profile/preferences.
- `auth_identity.provisioning_state=PENDING_PROFILE` and `active=false` until User confirms profile creation. Auth performs a local commit **before** the synchronous HTTP provisioning command; no cross-database ACID is claimed.
- User endpoint `POST /internal/identity/profiles` is accessible only inside the service network, is not mapped through Gateway, and requires a private 32+-character `X-Vellum-Provision-Key` token; bearer tokens are *not* accepted as its authentication. Rotate this independently of JWT signing material. For production, deploy with mTLS/network policy and stronger M2M authentication before exposing outside trusted private networking.
- The User database schema `V3__auth_owned_profile.sql` permits NULL password on Auth-managed accounts; legacy User account creation is unchanged. An Auth-provisioned account **cannot** authenticate with the legacy password login route.
- Unique constraints for UUID/email, `ON CONFLICT DO NOTHING` and subsequent record comparison distinguish retry from collision; no role escalation (User role is fixed).
- Auth cannot issue JWT for pending account; scheduled reconciliation retries the **same UUID** after User outage/timeout without requiring the original password. A pending account is not silently activated on a conflicting profile.
- Gateway accepts only cryptographically verified JWT and forwards only the bearer attached to `JwtAuthenticationToken`. It strips untrusted `X-User-*` identity headers and never emits a token. Gateway and downstream Services each validate issuer, audience, alg, type and signature with trusted JWKS.
- **No implicit dual issuer mode.** The default Compose stays on the User issuer. The dedicated opt-in `docker-compose.oidc-lote2.yml` explicitly switches all service validators together. **Cutover**, rollback and client replacements belong to #317.

## Development-only integration instructions

1. Create ignored RSA PEMs with `sh infra/scripts/security/generate_auth_poc_keys.sh`; keep 0600 and set `VELLUM_POC_RUN_AS="$(id -u):$(id -g)"`.
2. Configure existing `.env` for the base Compose and set `VELLUM_AUTH_DB_PASSWORD`, `VELLUM_POC_LOGIN`/`PASSWORD` (local-poc fixture), `VELLUM_INTERNAL_PROVISION_KEY` (strong unique random 32+ chars), `JWT_ISSUER=http://127.0.0.1:9000`, `CORS_ALLOWED_ORIGINS=http://127.0.0.1:9100`.
3. Launch `docker compose -f docker-compose.yml -f docker-compose.oidc-lote2.yml up --build`. **HTTP/cookie Secure=false and loopback key path are for local validation only. Do not deploy this overlay remotely.**
4. POST JSON `{"name":"Ada","email":"ada@example.invalid","password":"unique-password-12+"}` to `http://127.0.0.1:9000/v1/identity/register`, then use the existing PKCE test client with the same login. Query `/api/v1/users/me` through Gateway with the access token, then inspect at least one protected Catalog endpoint.
5. Test wrong provision key, different UUID same email, repeated same UUID, User unavailable followed by reconciliation, wrong `iss/aud/typ/kid`, ID Token as bearer, missing auth, JWT spoof, AS unavailable (warm and cold JWKS) and expected 401/403.
6. Run `mvn -B -ntp -Pauth-poc -pl services/auth-service,services/user-service,services/gateway-service -am verify`. Collect CI SHA, results, restart evidence and PR review before declaring completion.

## Explicit limitations

- Google upstream stays as User legacy integration until #308.
- No refresh tokens for public SPA yet (#309). Logout/SSO revocation not implemented by this batch.
- Real production SPA requires maintained OIDC library and full ID Token verification (#317); the demo harness is not that.
- Existing credentials in User remain for legacy users. **There are no production users to migrate**, but deleting or disabling legacy paths without inventory is not part of #307/#310.
- The 32-char shared internal credential is suitable only for opt-in private integration. Before production, require workload identity/mTLS and tested bypass prevention; this is a residual risk, not an undisclosed finished security guarantee.
- Operability: Auth DB backups, reliable reconciliation, login throttling, TLS, secrets rotation, audit logging and JWKS outage under load require empirical verification.
