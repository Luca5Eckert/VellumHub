# JWT JWKS rotation and RFC 9068 access tokens (issues #304, #305)

**Status:** implementation in PR #321, **not merged or deployed** until the PR and CI provide evidence. This change builds on the merged #163 RS256 implementation. It is not an OAuth2 Authorization Server, OIDC login, refresh-token service, or immediate token-revocation system.

## Trust boundaries and contracts

The **User Service is the temporary issuer**. Only it receives PKCS#8 RSA private keys. The Gateway and each API act as independent Resource Servers and fetch **public-only** keys from a configured internal JWKS URI, never from JWT `jku` or `x5u` fields. The issuer remains independently configurable; it need not equal the internal JWKS URL. The shared JWT audience is `api://vellumhub` until the Authorization Server PoC (#306) decides a better audience policy.

**JWKS URI:** `GET http://user-service:8080/.well-known/jwks.json` (from the internal Docker/Kubernetes network). It returns:
```json
{"keys":[{"kty":"RSA","use":"sig","kid":"local-key-1","alg":"RS256","n":"<PUBLIC_MODULUS>","e":"AQAB"}]}
```
The angle-bracket value above is illustrative and is never literal. No `d`, `p`, `q`, `dp`, `dq`, `qi`, PKCS#8 bytes, or any secret may appear in JWKS.

JWT header and representative access-token claims:
```json
{"alg":"RS256","typ":"at+jwt","kid":"local-key-1"}
```
```json
{
  "iss":"https://auth.vellumhub.local",
  "aud":["api://vellumhub"],
  "sub":"93bb05c3-48a4-43b4-b789-410782cb0c9f",
  "client_id":"vellumhub-web",
  "iat":1792400000,
  "exp":1792403600,
  "jti":"ee1f6672-9688-4d17-a284-22c5eec4666c",
  "roles":["USER"],
  "user_id":"93bb05c3-48a4-43b4-b789-410782cb0c9f"
}
```
These dates/UUIDs are examples, not live tokens. `sub` now holds the existing immutable user UUID. `user_id` is temporary, **must equal `sub`**, and is not authoritative. No email in `sub`. `roles` maps to `ROLE_*`. `scope` maps to `SCOPE_*` if an actual issuer grants it; no new scopes are invented by this PR. `client_id` is a configured identifier for today's first-party login, not evidence that OAuth client registration already exists.

Validators require RS256, trusted key ID, issuer, audience, type, valid `sub` UUID, `client_id`, `jti`, `iat`, `exp`, and optional matching legacy user_id. Default clock skew is 30 seconds. HS256, alg=none, ID Tokens, unrelated issuers/audiences, bad signatures and missing claims fail as 401. A valid token without required role/scope/ownership is 403 (except domain-policy 404 where applicable). Each domain keeps its local authorization/ownership checks.

## Environment

| Variable | Purpose |
| --- | --- |
| `JWT_ISSUER` | Exact trusted issuer in both signer and validators; use a stable environment-specific issuer |
| `JWT_AUDIENCE` | Logical API audience; initial value `api://vellumhub` |
| `JWT_JWKS_URI` | Trusted internal HTTP(S) URL to public JWKS; never token-supplied |
| `JWT_CLOCK_SKEW_SECONDS` | Allowed clock difference, 0-120 seconds (default 30) |
| `JWT_ACTIVE_KID` | Stable ID of the User Service signing key |
| `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` | Host PEM mounts for User Service **only** |
| `JWT_EXTRA_PUBLIC_KEYS` | Comma-separated `kid=file:/run/jwks-public/name.pem` mappings for published older/future public keys |
| `JWT_CLIENT_ID` | Configured `client_id` for current proprietary login |
| `JWT_EXPIRATION_MS` | Current JWT lifetime; influences retirement delay |

The sample issuer `https://auth.vellumhub.local` and Kubernetes `https://auth.example.invalid` are examples: choose and deploy a **real, consistent issuer** before a non-local rollout. Do not use them as accidental production identity authorities. Production must provision keys outside Git and must configure the final issuer explicitly. The emitted `iss` is not an IdP discovery endpoint.

For local Compose:
1. Generate the 2048+ bit matching PEMs as in `docs/SECURITY_RS256.md`.
2. Create `.local/jwks-public/` (empty until a rotation).
3. Copy `.env.example` to `.env` and configure keys/issuer/kid.
4. Run `docker compose config --quiet` and start the stack. Consumers do **not** mount private keys or active public PEMs.
5. Validate the internal endpoint via `docker compose exec user-service ...` or an authorized internal test workload. It is deliberately not exposed as a new public Gateway route.

For Kubernetes the User Service loads the private Secret, active public ConfigMap and optional `vellumhub-jwt-rotation-public` ConfigMap. Validators need only `JWT_JWKS_URI`, `JWT_ISSUER` and `JWT_AUDIENCE`. The sample ConfigMap issuer must be overridden before deployment. Restrict JWKS network access using the internal service/network policy; do not publish the issuer signing key.

## Normal rotation: A -> B without interruption

1. **Inventory** active issuer replicas, all verifier instances, token lifetime, skew, JWKS cache age, rejection rate and key custody. Ensure A is safely backed up for rollback.
2. **Provision B privately** in the secret manager; create B's public PEM. Keep A active. Make `JWT_EXTRA_PUBLIC_KEYS=key-b=file:/run/jwks-public/b.pem` and mount B's public PEM to that path **in every User Service issuer replica**; make A and B visible in the JWKS. Confirm key B is public-only.
3. **Wait for propagation** and verify A+B is present and trusted from each verifier. The default Nimbus cache holds remote JWKS in memory for approximately five minutes; to avoid an unsafe cutover, allow at least that interval and verify unknown-key refresh behavior in the actual runtime. A 60-second HTTP max-age response is not an immediate global invalidation guarantee.
4. **Promote B:** switch `JWT_ACTIVE_KID=key-b` and User Service active `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` to the B pair. Move A's public PEM into the additional public key set with `key-a=file:/run/jwks-public/a.pem`. Use a coordinated issuer deployment so different replicas do not unknowingly sign conflicting key IDs.
5. **Observe** both token types passing in Gateway and each API. Monitor JWT rejection rates and JWKS refresh failures.
6. **Retire A only after all A tokens expire**, plus clock skew and cache/propagation safety margin; remove A from the published set and config; confirm a fresh validator rejects it. With a configured 7-day JWT lifetime, do not retire A after only a five-minute JWKS cache wait.

Never reuse an old `kid` with new RSA material. No database schema is needed for this deployment-configured rotation. It is intentionally operator-controlled, not an automatic key-management service.

## Emergency compromise / rollback

If a signing private key leaks, stop signing with it, publish a replacement key and **force authenticated clients to reauthenticate**. Merely removing the compromised JWK from the issuer is insufficient: validators may retain it in local cache. Drain/restart or otherwise invalidate trusted JWKS caches **in every validator instance** and verify forged old-key tokens now fail. Enforce network isolation, assess token TTL and the lack of immediate access-token revocation, and record incident evidence. Unknown `kid` must fail closed.

For a botched normal promotion, restore previous issuer signing material and keep both trusted public keys published until tokens issued during the attempted promotion expire. Do not enable HS256 or trust arbitrary JWT header algorithms. Use synchronized configuration for all issuer replicas. No production rollout is claimed by this PR.

## Tests and evidence

* Signer tests: typed RS256 token, stable `kid`, correct UUID subject and required claims, bad signature, weak or malformed keys.
* Five independent verifier suites with ephemeral loopback JWKS: A, prepublish B, B signed, retire A, stale JWKS/offline fallback, unknown `kid` and issuer/audience/type/alg failures.
* Full `mvn -B -ntp -DexcludedGroups=distributed clean verify` and `-Pdistributed-e2e` tests in CI.
* Compose E2E `python3 infra/scripts/security/verify_rs256_compose.py` (now checks the new JWT contract and three resource paths).
* `docker compose config --quiet`, Kubernetes `kubectl kustomize deploy/kubernetes/base`, and static Postman contract drift.

Do not call this deployed or merged until CI, SHA and deployment evidence are available. The source of truth remains in Obsidian Vault; #306 may later replace the temporary User Service issuer without changing the Resource Server trust contract.

## HTTP failure contract

Both the general security exception handler and the OAuth2 Resource Server filter
use the same response handlers in each service. A malformed or rejected bearer
must not fall back to an empty framework-default response:

- Missing/invalid token: HTTP 401, `WWW-Authenticate: Bearer`, JSON
  `{"code":"UNAUTHORIZED","message":"Missing or invalid access token"}`.
- Authenticated request denied by the security filter: HTTP 403, JSON
  `{"code":"FORBIDDEN","message":"Insufficient permission"}`.

CORS rejections and domain-specific ownership/not-found responses are separate
contracts. Boundary regressions cover a malformed bearer through the servlet and
reactive filters and an authenticated servlet denial. Decoder tests separately
cover trusted-key rotation and JWKS outage; these do not prove a multi-replica
production rotation or an expired-cache recovery drill.

### Review evidence (20 October 2026)

At `2b0d69caf1331277e9cd66116639c23edeb72de6`, Actions run
[39511787678](https://github.com/Luca5Eckert/VellumHub/actions/runs/39511787678)
passed Maven verify, distributed tests and all five image builds/scans. CodeQL,
Postman validation and the recommendation benchmark also passed for that SHA.
These results do not validate subsequent changes to this branch.

The earlier run
[39406469035](https://github.com/Luca5Eckert/VellumHub/actions/runs/39406469035)
failed its Maven verify job at `62e71b523b01330f37e614b1a626bd454df584e8`;
distributed tests passed and image builds were skipped. Job logs could not be
retrieved, but the failure was reproduced from that exact commit locally:
`JwtSignatureVerificationTest.prepublicationAndRetirementOfKeysMaintainExpectedTrustBoundary`
in Gateway expected `JwtException` and received
`IllegalStateException: Could not obtain the keys` when JWKS was offline.
Subsequent commits map JWKS lookup failures to `BadJwtException`, including
synchronous reactive-decoder failures via `Mono.defer`.

Compose configuration and the Kustomize base rendered locally with example
configuration; all five deployments reference JWKS and only User Service receives
the private-key location. Rendering does not validate real secrets, connectivity,
NetworkPolicy enforcement, readiness or the running Compose smoke test. Those
runtime checks and final-SHA CI remain required before treating this as ready to
merge. The Vault implementation note retains the proposed/not-deployed status.

The review additionally requires a nonblank string `kid` in the shared validator;
otherwise a correctly signed token without `kid` can match the sole published
key. All five decoder suites now reject that case. The focused Java 21 reactor
run passed **36 tests, 0 failures, 0 errors, 0 skipped**, including the HTTP
contract regressions, token issuance and rotation suites. Command:

```sh
mvn -B -ntp -Dtest=UserSecurityBoundaryTest,GatewaySecurityBoundaryTest,JwtSignatureVerificationTest,JwtAccessTokenTest,JwtKeyRingTest,JwtTokenProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The local runtime required Mockito 5.20.0 as an explicit `-javaagent` because
self-attachment is unavailable there; no production/runtime JVM configuration
was changed. Kustomize base and local overlay rendered successfully. Full
`clean verify` and distributed/container execution for the new commit must be
confirmed by CI; this focused run does not replace them.
