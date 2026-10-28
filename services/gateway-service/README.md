# Gateway Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.x-green)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-Gateway-blue)](https://spring.io/projects/spring-cloud-gateway)
[![WebFlux](https://img.shields.io/badge/Spring-WebFlux-brightgreen)](https://docs.spring.io/spring-framework/reference/web/webflux.html)
[![Redis](https://img.shields.io/badge/Redis-rate%20limit-red)](https://redis.io/)

The Gateway Service exists to be VellumHub's public edge: every client request enters here before it reaches user, catalog, engagement, or recommendation services.

It is intentionally thin. It owns traffic policy and API documentation discovery, not business state or downstream OpenAPI contracts.

## Why This Service Exists

- Give the platform one public HTTP entry point.
- Route stable `/api/v1/**` prefixes to internal service URLs.
- Enforce JWT authentication before protected requests reach downstream services.
- Apply route-specific rate limits backed by Redis.
- Keep downstream services private to the Docker network in the default compose topology.
- Provide one Swagger UI entry point while keeping each service responsible for its own OpenAPI contract.

## What It Owns

| Concern | Owned here |
|---|---|
| Public route prefixes | `/api/v1/auth`, `/api/v1/users`, `/api/v1/catalog`, `/api/v1/engagement`, `/api/v1/recommendations` |
| Edge authentication | JWT validation for protected gateway routes |
| Rate limiting | Redis-backed request quotas by IP or user context |
| Reactive proxying | Spring Cloud Gateway WebFlux route forwarding |
| API documentation discovery | Central Swagger UI at `/docs` and proxies under `/docs/{service}/v3/api-docs` |
| Edge observability | Actuator health, metrics, info, and Prometheus endpoints |

## What It Does Not Own

- User identity storage or password rules.
- Catalog, engagement, or recommendation domain behavior.
- Kafka event production or consumption.
- Service databases.
- Swagger/OpenAPI generation for downstream APIs.

Each downstream service still validates JWTs in its own security configuration; the gateway is an ingress boundary, not the only authorization boundary. Each downstream service also remains the source of truth for its own OpenAPI document.

## Routing Map

Configured in `src/main/resources/application.yml`.

### Business routes

| Route ID | Public prefix | Downstream service | Strip prefix |
|---|---|---|---:|
| `auth` | `/api/v1/auth/**` | `user-service` | 2 |
| `user` | `/api/v1/users/**` | `user-service` | 2 |
| `catalogs` | `/api/v1/catalog/**` | `catalog-service` | 3 |
| `engagements` | `/api/v1/engagement/**` | `engagement-service` | 3 |
| `recommendations` | `/api/v1/recommendations/**` | `recommendation-service` | 2 |

Example:

```http
GET /api/v1/recommendations/recommendations
```

is forwarded to:

```http
GET /recommendations
```

### OpenAPI routes

The gateway does not merge or copy OpenAPI documents. It proxies each service-owned document behind a stable documentation path used by the central Swagger UI.

| Route ID | Gateway path | Downstream document |
|---|---|---|
| `user-openapi` | `/docs/user/v3/api-docs` | `user-service:/v3/api-docs` |
| `catalog-openapi` | `/docs/catalog/v3/api-docs` | `catalog-service:/v3/api-docs` |
| `engagement-openapi` | `/docs/engagement/v3/api-docs` | `engagement-service:/v3/api-docs` |
| `recommendation-openapi` | `/docs/recommendation/v3/api-docs` | `recommendation-service:/v3/api-docs` |

Open `http://localhost:8080/docs` and choose the service from the Swagger UI selector. If one downstream service is unavailable, only that service's document fails to load; the gateway does not retain a stale copy.

## Security and Rate Limiting

| Route group | Authentication | Replenish rate | Burst capacity | Key strategy |
|---|---|---:|---:|---|
| Auth/User flows | Auth routes public, user routes protected | 5 | 10 | IP |
| Catalog/Engagement | Protected | 30 | 60 | User, principal, then IP |
| Recommendations | Protected | 20 | 40 | User, principal, then IP |
| API documentation | Public for local/development inspection | — | — | — |

JWT settings:

- Algorithm: RSASSA-PKCS1-v1_5 with SHA-256 (JWT `RS256`), explicitly allowlisted in Nimbus.
- Spring Resource property: `jwt.public-key-location` (from `JWT_PUBLIC_KEY_LOCATION`).
- Verification uses only a mounted X.509 PEM **public key**. No signing key, shared HMAC secret, JWKS URL, or synchronous issuer call is required.
- HS256 tokens are rejected. See [RS256 operations](../../docs/SECURITY_RS256.md).

The public documentation surface is intentionally limited to `/docs`, `/docs/**`, `/swagger-ui/**`, and the Swagger bootstrap endpoint `/v3/api-docs/swagger-config`. Business routes keep their existing authentication policy.

## Configuration

| Variable | Purpose | Default |
|---|---|---|
| `USER_SERVICE_URL` | User/auth target, including OpenAPI proxy | `http://user-service:8080` |
| `CATALOG_SERVICE_URL` | Catalog target, including OpenAPI proxy | `http://catalog-service:8080` |
| `ENGAGEMENT_SERVICE_URL` | Engagement target, including OpenAPI proxy | `http://engagement-service:8080` |
| `RECOMMENDATION_SERVICE_URL` | Recommendation target, including OpenAPI proxy | `http://recommendation-service:8080` |
| `JWT_PUBLIC_KEY_LOCATION` | URI of the X.509 PEM verification key | required, fail closed |
| `SPRING_DATA_REDIS_HOST` | Redis host | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |

## Observability

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Liveness/readiness signal |
| `/actuator/info` | Application info |
| `/actuator/metrics` | JVM and gateway metrics |
| `/actuator/prometheus` | Prometheus scrape format |

Gateway route logging is currently configured at `TRACE` for Spring Cloud Gateway. That is useful for local routing diagnostics and should be tightened for production hardening.

## Run Locally

Standalone:

```bash
cd services/gateway-service
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd services/gateway-service
.\mvnw.cmd spring-boot:run
```

For the centralized Swagger UI, run the default Docker Compose stack so the gateway can resolve all four downstream services:

```bash
docker-compose up -d
```

Access:

| Resource | URL |
|---|---|
| Gateway base URL | `http://localhost:8080` |
| Central Swagger UI | `http://localhost:8080/docs` |
| User OpenAPI through gateway | `http://localhost:8080/docs/user/v3/api-docs` |
| Catalog OpenAPI through gateway | `http://localhost:8080/docs/catalog/v3/api-docs` |
| Engagement OpenAPI through gateway | `http://localhost:8080/docs/engagement/v3/api-docs` |
| Recommendation OpenAPI through gateway | `http://localhost:8080/docs/recommendation/v3/api-docs` |
| Health | `http://localhost:8080/actuator/health` |

When a domain service is run directly, its own `/swagger-ui/index.html` and `/v3/api-docs` endpoints remain available. The central gateway UI is a discovery layer, not a replacement for service-local documentation.

## Verify

```powershell
cd services/gateway-service
.\mvnw.cmd test
```

`OpenApiDocumentationConfigurationTest` guards the four Swagger UI entries and their matching gateway routes against accidental configuration drift.

For platform topology and cross-service flows, see the [root README](../../README.md). For the complete API documentation flow, see [API Documentation](../../docs/api-documentation.md).

## SEC-01 HTTP security baseline

The API remains JWT bearer-based; issue #163 migrates the signature to RS256 with independent public-key validation on this branch (coordinated cutover required). Service security continues to validate tokens independently of the Gateway. Swagger/OpenAPI is opt-in through `VELLUM_DOCS_ENABLED=true` in local Compose; default deployment configuration denies documentation. Health and Prometheus remain unauthenticated at the service HTTP layer for **internal** probe/scrape traffic, so network and Ingress must keep these ports private. CORS uses explicit `CORS_ALLOWED_ORIGINS` and preserves credentialed browser requests for compatibility. JWT Bearer remains the authenticator; any future cookie-session authentication requires separate CSRF review. See [SEC-01 HTTP boundary contract](../../docs/SECURITY_HTTP_BOUNDARY.md) for policy matrix, tests and rollback.

The Gateway no longer exposes the legacy User `/api/v1/auth/*` routes. Client-supplied identity headers are removed before forwarding, and only a bearer already validated by Spring Security is relayed downstream. `VELLUM_TRUSTED_PROXY_CIDRS` (comma-separated CIDRs; empty by default) must list only reverse proxies that append verified X-Forwarded-For hops. The Redis quota values are unchanged; the default without a trusted proxy uses the TCP peer address. The application logs Gateway at INFO rather than TRACE by default.


### Security JWT/JWKS transition (#304, #305)

Gateway and every domain Resource Server validate only RS256 access tokens issued by `auth-service`, using `JWT_JWKS_URI=http://auth-service:9000/oauth2/jwks` inside the runtime network plus the configured issuer/audience. Unknown `kid`, invalid signature/type/issuer/audience and malformed identity claims fail closed. Only Auth owns signing material; User, Catalog, Engagement, Recommendation and Gateway hold no private JWT key and perform no per-request identity lookup. Read [docs/SECURITY_JWKS_RFC9068.md](../../docs/SECURITY_JWKS_RFC9068.md) for configuration, key rotation, outage behavior and safe cutover. Not yet deployed.

## SEC-09 — Verified bearer relay (opt-in issuer change)

`VerifiedBearerRelayFilter` removes untrusted incoming Authorization headers and forwards only the access token from a validated `JwtAuthenticationToken`, never an ID Token or forwarded username. Public legacy login routes do not receive Authorization. SecurityChain verifies RS256 JWKS, issuer, audience and RFC 9068 type before Gateway filtering. Internal provisioning paths are denied and not routed.

The default Compose runtime uses `auth-service` as the single issuer/JWKS source. `docker-compose.oidc-lote2.yml` is retained only as a local regression harness for the PKCE/OIDC flow; it is no longer the activation mechanism and there is no legacy or dual-issuer fallback.
