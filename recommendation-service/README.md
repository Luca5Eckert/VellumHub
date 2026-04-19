# 🤖 Recommendation Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x%20%2F%204.0.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![pgvector](https://img.shields.io/badge/pgvector-enabled-blue)](https://github.com/pgvector/pgvector)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black)](https://kafka.apache.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0-brightgreen)](https://swagger.io/specification/)

The **Recommendation Service** is VellumHub’s event-fed read model for personalized recommendations.

It consumes catalog and interaction streams, maintains local vector/state tables, and serves low-latency recommendations without synchronous cross-service dependency in the query path.

---

## Table of Contents

- [Service Role in VellumHub](#service-role-in-vellumhub)
- [Module Map](#module-map)
- [HTTP API Surface](#http-api-surface)
- [Event Contract](#event-contract)
- [Vector and Data Model](#vector-and-data-model)
- [Security and Observability](#security-and-observability)
- [Quick Start](#quick-start)

---

## Service Role in VellumHub

- Maintains recommendation state from Kafka streams (ECST)
- Stores 384-dimensional vectors in pgvector-backed tables
- Applies profile learning and serves ranked recommendation responses
- Uses retry + DLT strategy for resilient consumers

---

## Module Map

| Module | Responsibility |
|---|---|
| `book_feature` | Embedding generation/storage and popularity metadata per book |
| `user_profile` | Incremental user vector updates from rating/progress/reaction/preference events |
| `recommendation` | Query path: ranking, pagination, and response assembly |

---

## HTTP API Surface

Base path exposed by this service:

- `/recommendations`

Endpoint:

```http
GET /recommendations?limit=10&offset=0
```

- Requires JWT bearer token
- Uses authenticated user ID to fetch personalized results

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

Kafka retry/DLT behavior is configured in `share/kafka/config/KafkaRetryConfig`.

---

## Vector and Data Model

Main database: `recommendation_db` (PostgreSQL + pgvector).

Core tables:

- `book_features`
  - `embedding vector(384)`
  - `popularity_score`
- `user_profiles`
  - `profile_vector vector(384)`
  - `interacted_book_ids uuid[]`
  - `total_engagement_score`
- `recommendations`
  - denormalized metadata for efficient response composition

The service applies L2 normalization to profile vectors after learning updates to keep cosine-based comparisons stable.

---

## Security and Observability

- JWT-protected recommendation endpoint
- Spring Actuator enabled: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Swagger/OpenAPI: `/swagger-ui/index.html`, `/v3/api-docs`

---

## Quick Start

### Run locally

```bash
cd recommendation-service
./mvnw spring-boot:run
```

### Run via Docker Compose (from repo root)

```bash
docker-compose up -d recommendation-service
```

### Default local access

- Service: `http://localhost:8085`
- Swagger UI: `http://localhost:8085/swagger-ui/index.html`
- OpenAPI: `http://localhost:8085/v3/api-docs`

---

For system-wide architecture (Gateway, ECST backbone, cold-start strategy), see [VellumHub root README](../README.md).
