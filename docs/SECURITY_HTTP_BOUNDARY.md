# SEC-01: HTTP boundary and security verification

This document is the route and network contract for issue #302. OAuth2/OIDC, RS256 and Google account linking are separate work.

## Routes and trust boundaries

| Gateway path | Method | Principal required | Notes |
| --- | --- | --- | --- |
| /api/v1/auth/login | POST | no | User Service /auth/login |
| /api/v1/auth/register | POST | no | User Service /auth/register |
| /api/v1/auth/google | POST | no | User Service /auth/google, provider token checked in service |
| /api/v1/auth/** (other) | any | yes | no wildcard public auth access |
| /api/v1/users/** | service-defined | yes | per-method and ownership policy downstream |
| /api/v1/catalog/** | service-defined | yes | downstream public /files/books/** must be inspected separately |
| /api/v1/engagement/** | service-defined | yes | authorization downstream |
| /api/v1/recommendations/** | service-defined | yes | authorization downstream |
| /docs, /docs/**, /swagger-ui/**, /v3/api-docs/** | GET | opt-in only | VELLUM_DOCS_ENABLED=true on local Compose |
| /actuator/health/** | GET | no HTTP token | internal probing; public ingress must not route |
| /actuator/prometheus | GET | no HTTP token | internal scraping only; public ingress must not route |

Every downstream service validates the existing HS256 access token. Role/ownership checks remain downstream. 401 indicates missing/invalid bearer authentication at the HTTP security chain; 403 indicates failed authorization where applicable. Existing User Service domain-level login failures return its legacy error contract (400 for invalid Google/provider credential). Changing that contract is not part of SEC-01.

## Configuration and deployment

* `VELLUM_DOCS_ENABLED` defaults to `false` in all services. Compose explicitly enables local API documentation even though it runs the `prod` Spring profile. Do not enable on a public ingress.
* `CORS_ALLOWED_ORIGINS` is a comma-separated list of exact browser origins. Bearer tokens are passed in the Authorization header; allowCredentials remains enabled with exact allowed origins for compatibility with existing browser clients, but cookies are not accepted as authentication by the current API. Reassess CSRF before introducing cookie sessions. CORS is not authorization and clients without browser enforcement still need tokens.
* `VELLUM_TRUSTED_PROXY_CIDRS` defaults to empty. In that mode, Gateway ignores X-Forwarded-For entirely and identifies a client by its TCP peer. Set explicit IP/CIDR ranges only for ingress proxies that append validated peer IPs. An incorrectly configured ingress may cause clients to share one rate-limit bucket.
* Rate-limit settings remain: auth and users 5 replenish/10 burst by IP; catalog and engagement 30/60, recommendation 20/40 by authenticated user (fallback IP).
* Identity assertion headers such as X-User-Id, X-User-Roles and X-Authenticated-User are stripped by the Gateway before routing. Downstream identity comes from the validated JWT, not client headers.
* HTTP routes and authorization work without OAuth2 Authorization Server; login and JWT remain legacy HS256 until their separate issues.

Kubernetes base ConfigMap explicitly sets VELLUM_DOCS_ENABLED=false and VELLUM_TRUSTED_PROXY_CIDRS="" (override the latter with actual ingress CIDRs for fair per-client quotas). Production Kustomize ingress exports only /api/v1 on the Gateway; internal services use ClusterIP. Existing NetworkPolicy still admits traffic within the VellumHub namespace, including operational scrapers. This is **network isolation**, not Actuator authentication. Do not expose service ports publicly or add wildcard ingress routes for /docs or /actuator. Confirm the CNI enforces NetworkPolicy and restrict namespace access.

## Tests and evidence

Run:

```bash
mvn -B -ntp -pl services/gateway-service test
mvn -B -ntp -pl services/user-service test
mvn -B -ntp clean verify
docker compose config
docker compose up -d --build
bash infra/scripts/smoke-tests/security-http.sh
bash infra/scripts/smoke-tests/test-compose-runtime.sh
docker compose down
kubectl kustomize deploy/kubernetes/overlays/prod
```

Optional E2E credentials and a Google identity token can be supplied to the smoke script. A missing external Google fixture means the **positive Google login E2E is not yet proven**; an invalid-provider-token test alone does not meet that acceptance criterion. GatewaySecurityBoundaryTest asserts that negative-auth requests containing Authorization and Cookie canaries do not appear in captured Gateway application log messages. Independently compare structured production-like log output and **exported OpenTelemetry spans** against canary secrets, as the unit/integration test does not validate an actual OTEL collector/exporter. Never print the secrets themselves into CI artifacts.

## Rollback and residual risk

Rollback is a deployment of the previous immutable image and matching configuration, not a security bypass toggle. Validate local/Google login, Redis quotas, documentation, CORS and Prometheus before promoting. HS256 shared-key exposure, lack of explicit JWT iss/aud and email-based Google linkage remain known risks for #163/#305/#303. Token revocation and OIDC scopes are not claimed here.
