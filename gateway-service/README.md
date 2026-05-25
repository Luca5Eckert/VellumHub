# Gateway Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.x-green)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-Gateway-blue)](https://spring.io/projects/spring-cloud-gateway)
[![WebFlux](https://img.shields.io/badge/Spring-WebFlux-brightgreen)](https://docs.spring.io/spring-framework/reference/web/webflux.html)
[![Redis](https://img.shields.io/badge/Redis-rate%20limit-red)](https://redis.io/)

The Gateway Service exists to be VellumHub's public edge: every client request enters here before it reaches user, catalog, engagement, or recommendation services.

It is intentionally thin. It owns traffic policy, not business state.

## Why This Service Exists

- Give the platform one public HTTP entry point.
- Route stable `/api/v1/**` prefixes to internal service URLs.
- Enforce JWT authentication before protected requests reach downstream services.
- Apply route-specific rate limits backed by Redis.
- Keep downstream services private to the Docker network in the default compose topology.

## What It Owns

| Concern | Owned here |
|---|---|
| Public route prefixes | `/api/v1/auth`, `/api/v1/users`, `/api/v1/catalog`, `/api/v1/engagement`, `/api/v1/recommendations` |
| Edge authentication | JWT validation for protected gateway routes |
| Rate limiting | Redis-backed request quotas by IP or user context |
| Reactive proxying | Spring Cloud Gateway WebFlux route forwarding |
| Edge observability | Actuator health, metrics, info, and Prometheus endpoints |

## What It Does Not Own

- User identity storage or password rules.
- Catalog, engagement, or recommendation domain behavior.
- Kafka event production or consumption.
- Service databases.
- Swagger/OpenAPI generation for downstream APIs.

Each downstream service still validates JWTs in its own security configuration; the gateway is an ingress boundary, not the only authorization boundary.

## Routing Map

Configured in `src/main/resources/application.yml`.

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

## Security and Rate Limiting

| Route group | Authentication | Replenish rate | Burst capacity | Key strategy |
|---|---|---:|---:|---|
| Auth/User flows | Auth routes public, user routes protected | 5 | 10 | IP |
| Catalog/Engagement | Protected | 30 | 60 | User, principal, then IP |
| Recommendations | Protected | 20 | 40 | User, principal, then IP |

JWT settings:

- Algorithm: HMAC SHA-256.
- Secret property: `spring.security.oauth2.resourceserver.jwt.secret-key`.
- Environment variable: `JWT_KEY`.
- The configured key must be Base64-compatible because the gateway decodes it before building the verifier.

## Configuration

| Variable | Purpose | Default |
|---|---|---|
| `USER_SERVICE_URL` | User/auth target | `http://user-service:8080` |
| `CATALOG_SERVICE_URL` | Catalog target | `http://catalog-service:8080` |
| `ENGAGEMENT_SERVICE_URL` | Engagement target | `http://engagement-service:8080` |
| `RECOMMENDATION_SERVICE_URL` | Recommendation target | `http://recommendation-service:8080` |
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
cd gateway-service
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd gateway-service
.\mvnw.cmd spring-boot:run
```

With Docker Compose from the repository root:

```bash
docker-compose up -d gateway-service redis-gateway
```

Access:

| Resource | URL |
|---|---|
| Gateway base URL | `http://localhost:8080` |
| Health | `http://localhost:8080/actuator/health` |

## Verify

```powershell
cd gateway-service
.\mvnw.cmd test
```

For platform topology and cross-service flows, see the [root README](../README.md).
