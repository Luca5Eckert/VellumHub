# 🚪 Gateway Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.x-green)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-Gateway-blue)](https://spring.io/projects/spring-cloud-gateway)
[![WebFlux](https://img.shields.io/badge/Spring-WebFlux-brightgreen)](https://docs.spring.io/spring-framework/reference/web/webflux.html)
[![Redis](https://img.shields.io/badge/Redis-Rate%20Limit-red)](https://redis.io/)

The **Gateway Service** is the single entry point for VellumHub. It centralizes routing, JWT authentication enforcement, and Redis-backed request rate limiting.

---

## Table of Contents

- [Service Role in VellumHub](#service-role-in-vellumhub)
- [Routing Map](#routing-map)
- [Security Model](#security-model)
- [Rate Limiting Model](#rate-limiting-model)
- [Configuration and Environment Variables](#configuration-and-environment-variables)
- [Observability](#observability)
- [Quick Start](#quick-start)

---

## Service Role in VellumHub

- Public ingress for all client traffic
- Reactive routing/proxy layer to internal microservices
- JWT resource server for protected routes
- Global rate-limit enforcement before traffic reaches downstream services

---

## Routing Map

Configured route prefixes in `application.yml`:

| Route ID | Public Prefix | Downstream Service | Strip Prefix |
|---|---|---|---|
| `user` | `/api/v1/users/**` | `user-service` | `2` |
| `auth` | `/api/v1/auth/**` | `user-service` | `2` |
| `catalogs` | `/api/v1/catalog/**` | `catalog-service` | `3` |
| `engagements` | `/api/v1/engagement/**` | `engagement-service` | `3` |
| `recommendations` | `/api/v1/recommendations/**` | `recommendation-service` | `2` |

Example behavior:

- `GET /api/v1/recommendations/recommendations` → forwarded to recommendation service as `GET /recommendations`
- `POST /api/v1/auth/login` stays public and is forwarded to user service auth module

---

## Security Model

`GatewaySecurityConfig` enforces:

- `permitAll`: `/actuator/**`, `/api/v1/auth/**`
- `authenticated`: every other route

JWT decoding:

- HMAC SHA-256 (`HS256`)
- Secret from `spring.security.oauth2.resourceserver.jwt.secret-key`
- Implemented with reactive decoder (`NimbusReactiveJwtDecoder`)

> Important: the configured secret must be Base64-compatible because it is decoded with `Decoders.BASE64.decode(...)`.

---

## Rate Limiting Model

Redis RequestRateLimiter is attached per route.

### Key Resolvers

- `ipKeyResolver`: rate-limit by client IP
- `userKeyResolver` (primary): prefers JWT claim `user_id`, fallback to principal name, then IP

### Current quotas (from gateway config)

| Route Group | Replenish Rate | Burst Capacity | Key Strategy |
|---|---:|---:|---|
| Auth/User public-ish flows | `5` | `10` | IP |
| Catalog/Engagement routes | `30` | `60` | User/Principal/IP fallback |
| Recommendation routes | `20` | `40` | User/Principal/IP fallback |

---

## Configuration and Environment Variables

Key variables used by gateway:

- `USER_SERVICE_URL` (default: `http://user-service:8080`)
- `CATALOG_SERVICE_URL` (default: `http://catalog-service:8080`)
- `ENGAGEMENT_SERVICE_URL` (default: `http://engagement-service:8080`)
- `RECOMMENDATION_SERVICE_URL` (default: `http://recommendation-service:8080`)
- `JWT_KEY`
- `SPRING_DATA_REDIS_HOST` (default: `localhost`)
- `SPRING_DATA_REDIS_PORT` (default: `6379`)

Gateway local port:

- `8080` (configured in `application.yml`)

---

## Observability

Actuator endpoints exposed:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

The gateway also uses detailed route logging (`org.springframework.cloud.gateway: TRACE`) for request forwarding diagnostics.

---

## Quick Start

### Run standalone

```bash
cd gateway-service
./mvnw spring-boot:run
```

### Run with Docker Compose (from repo root)

```bash
docker-compose up -d gateway-service redis-gateway
```

### Access

- Gateway base URL: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`

---

For platform-wide architecture context, see [VellumHub root README](../README.md).
