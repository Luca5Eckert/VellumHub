# 🤖 Recommendation Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![pgvector](https://img.shields.io/badge/pgvector-enabled-blue)](https://github.com/pgvector/pgvector)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black)](https://kafka.apache.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0-brightgreen)](https://swagger.io/specification/)

The **Recommendation Service** is VellumHub’s event-fed recommendation read model.

It consumes book and user interaction streams, maintains local vector/search state, and serves personalized recommendations in a low-latency path.

---

## Table of Contents

- [Service Role in VellumHub](#service-role-in-vellumhub)
- [Domain Modules](#domain-modules)
- [HTTP API Surface](#http-api-surface)
- [Event Contract](#event-contract)
- [Vector Pipeline and Ranking](#vector-pipeline-and-ranking)
- [Data Ownership](#data-ownership)
- [Security and Observability](#security-and-observability)
- [Quick Start](#quick-start)

---

## Service Role in VellumHub

- ECST consumer for catalog and interaction topics
- Local vector store and recommendation serving layer
- No synchronous dependency required at query-time for primary recommendation state

---

## Domain Modules

| Module | Responsibility |
|---|---|
| `book_feature` | Embedding generation + persistence (`vector(384)`) and popularity signals |
| `user_profile` | Profile vector updates from ratings/progress/reactions/preferences |
| `recommendation` | Recommendation retrieval, ranking, and response mapping |

---

## HTTP API Surface

Base path:

- `/recommendations`

Endpoint:

```http
GET /recommendations?limit=10&offset=0
```

Response is personalized using authenticated user ID extracted from JWT context.

---

## Event Contract

Consumed topics:

- `created-book`
- `updated-book`
- `deleted-book`
- `created-rating`
- `create_user_preference`
- `updated-progress`
- `user-reaction-changed`

Retry + DLT behavior for consumers is configured in `share/kafka/config/KafkaRetryConfig`.

---

## Vector Pipeline and Ranking

- Book embeddings stored in `book_features.embedding` as `vector(384)`
- User profiles stored in `user_profiles.profile_vector` as `vector(384)`
- User profile learning applies updates from interaction events and performs L2 normalization in domain logic
- Recommendation serving combines vector relevance with additional ranking signals (e.g., popularity metadata)

---

## Data Ownership

Primary DB: `recommendation_db` (PostgreSQL + pgvector).

Core tables:

- `book_features`
- `user_profiles`
- `recommendations` (denormalized cache/read table used by recommendation query assembly)

This service owns derived recommendation state but not upstream catalog/user source-of-truth entities.

---

## Security and Observability

- JWT-protected endpoint
- Actuator: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI: `/v3/api-docs`

---

## Quick Start

### Run locally

```bash
cd recommendation-service
./mvnw spring-boot:run
```

### Run via Docker Compose

```bash
docker-compose up -d recommendation-service
```

### Configuration highlights

- `SERVER_PORT` (default `8085`)
- `SPRING_DATASOURCE_*`
- `KAFKA_BOOTSTRAP_SERVERS`
- `JWT_KEY`

---

See [root README](../README.md) for full system flow.
