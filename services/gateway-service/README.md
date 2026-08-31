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

- Algorithm: HMAC SHA-256.
- Secret property: `spring.security.oauth2.resourceserver.jwt.secret-key`.
- Environment variable: `JWT_KEY`.
- The configured key must be Base64-compatible because the gateway decodes it before building the verifier.

The public documentation surface is intentionally limited to `/docs`, `/docs/**`, `/swagger-ui/**`, and the Swagger bootstrap endpoint `/v3/api-docs/swagger-config`. Business routes keep their existing authentication policy.

## Configuration

| Variable | Purpose | Default |
|---|---|---|
| `USER_SERVICE_URL` | User/auth target, including OpenAPI proxy | `http://user-service:8080` |
| `CATALOG_SERVICE_URL` | Catalog target, including OpenAPI proxy | `http://catalog-service:8080` |
| `ENGAGEMENT_SERVICE_URL` | Engagement target, including OpenAPI proxy | `http://engagement-service:8080` |
| `RECOMMENDATION_SERVICE_URL` | Recommendation target, including OpenAPI proxy | `http://recommendation-service:8080` |
| `JWT_KEY` | Gateway JWT verification key | required for realistic local/prod runs |
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
