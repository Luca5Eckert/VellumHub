# ⭐ Engagement Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x%20%2F%204.0.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black)](https://kafka.apache.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0-brightgreen)](https://swagger.io/specification/)

The **Engagement Service** captures user interaction signals (ratings, reactions, reading sessions) and maintains local replicated catalog state (`book_snapshot`) through ECST.

It is the interaction producer of VellumHub, emitting events that continuously update recommendation profiles.

---

## Table of Contents

- [Service Role in VellumHub](#service-role-in-vellumhub)
- [Module Map](#module-map)
- [HTTP API Surface](#http-api-surface)
- [Event Contract](#event-contract)
- [Data Ownership](#data-ownership)
- [Security and Observability](#security-and-observability)
- [Quick Start](#quick-start)

---

## Service Role in VellumHub

- Captures explicit user feedback and interaction intent
- Publishes interaction streams consumed by Recommendation Service
- Keeps local `book_snapshot` synchronized from catalog events for query-time independence

---

## Module Map

| Module | Responsibility |
|---|---|
| `rating` | Create/update/delete/query ratings |
| `reaction` | Create/update/get reactions by user/book context |
| `reading_session_entry` | Emits reading-session interaction events |
| `book_snapshot` | Local replica of catalog state via Kafka consumers |

---

## HTTP API Surface

Base paths exposed by this service:

- `/rating`
- `/reactions`
- `/reading-session-entries`

Representative endpoints:

```http
POST   /rating
GET    /rating/{userId}
GET    /rating/me
PUT    /rating/{ratingId}
DELETE /rating/{ratingId}
GET    /rating/{bookId}

POST   /reactions
PUT    /reactions/{id}
GET    /reactions/{id}
GET    /reactions

POST   /reading-session-entries
```

---

## Event Contract

Produced topics:

- `created-rating`
- `user-reaction-changed`
- reading-session events published by `reading_session_entry`

Consumed topics:

- `created-book`
- `updated-book`
- `deleted-book`

Retry/DLT handling is configured for Kafka consumers (see `KafkaRetryConfig`).

---

## Data Ownership

Main database: `engagement_db`.

Core entities include:

- `rating`
- `reaction`
- `book_snapshot`
- reading session entry persistence model

This service is authoritative for engagement history and interaction events.

---

## Security and Observability

- JWT-protected endpoints via Spring Security
- User-scoped mutations enforced with authenticated user context
- Actuator enabled: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Swagger/OpenAPI: `/swagger-ui/index.html`, `/v3/api-docs`

---

## Quick Start

### Run locally

```bash
cd engagement-service
./mvnw spring-boot:run
```

### Run via Docker Compose (from repo root)

```bash
docker-compose up -d engagement-service
```

### Default local access

- Service: `http://localhost:8083`
- Swagger UI: `http://localhost:8083/swagger-ui/index.html`
- OpenAPI: `http://localhost:8083/v3/api-docs`

---

For full platform architecture and event flow, see [VellumHub root README](../README.md).
