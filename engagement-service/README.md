# ⭐ Engagement Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black)](https://kafka.apache.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0-brightgreen)](https://swagger.io/specification/)

The **Engagement Service** captures interaction signals from readers and publishes these signals to power recommendation profile updates.

It also maintains a local `book_snapshot` model from catalog events to reduce synchronous coupling.

---

## Table of Contents

- [Service Role in VellumHub](#service-role-in-vellumhub)
- [Domain Modules](#domain-modules)
- [HTTP API Surface](#http-api-surface)
- [Event Contract](#event-contract)
- [Data Ownership](#data-ownership)
- [Security Rules](#security-rules)
- [Observability and API Docs](#observability-and-api-docs)
- [Quick Start](#quick-start)

---

## Service Role in VellumHub

- Captures explicit feedback (`rating`, `reaction`)
- Publishes recommendation-relevant interaction events
- Replicates catalog deltas locally via ECST consumers

---

## Domain Modules

| Module | Responsibility |
|---|---|
| `rating` | CRUD/query of ratings with filters and pagination |
| `reaction` | User reaction lifecycle and lookup |
| `reading_session_entry` | Reading session event publishing |
| `book_snapshot` | Local copy of book records from catalog events |

---

## HTTP API Surface

Base paths:

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
- reading-session topic(s) from `reading_session_entry`

Consumed topics:

- `created-book`
- `updated-book`
- `deleted-book`

Kafka retry and DLT processing are configured in `share/config/KafkaRetryConfig`.

---

## Data Ownership

Primary DB: `engagement_db`.

Core persisted domains include ratings, reactions, and local book snapshot state used in engagement workflows.

---

## Security Rules

- JWT authentication required
- User context extracted server-side for write operations
- Ownership checks in update/delete interaction operations

---

## Observability and API Docs

- Actuator: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI: `/v3/api-docs`

---

## Quick Start

### Run locally

```bash
cd engagement-service
./mvnw spring-boot:run
```

### Run via Docker Compose

```bash
docker-compose up -d engagement-service
```

### Configuration highlights

- `SPRING_DATASOURCE_*`
- `KAFKA_BOOTSTRAP_SERVERS`
- `JWT_KEY`

---

See [root README](../README.md) for full event backbone context.
