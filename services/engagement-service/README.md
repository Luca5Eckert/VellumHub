# Engagement Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-producer%20%2F%20consumer-black)](https://kafka.apache.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Springdoc-brightgreen)](https://springdoc.org/)

The Engagement Service exists to capture what readers do with books: ratings, reactions, and reading-progress history derived from catalog events.

It turns user behavior into durable engagement records and Kafka signals that recommendation can learn from.

## Why This Service Exists

- Own reader feedback such as ratings and reactions.
- Publish recommendation-relevant interaction events.
- Maintain local book snapshots from catalog events.
- Store replicated reading progress history from catalog progress events.
- Keep engagement workflows independent from synchronous catalog lookups.

## What It Owns

| Concern | Owned here |
|---|---|
| Ratings | User rating creation, update, delete, and lookup |
| Reactions | User reaction lifecycle and lookup |
| Book snapshots | Local book copy populated from catalog events |
| Reading history | Reading session/progress entries consumed from Kafka |
| Database | `engagement_db` |
| Engagement events | `created-rating`, `user-reaction-changed` |

## What It Does Not Own

- Book source-of-truth data.
- Current reading progress writes.
- User identity or JWT issuing.
- Recommendation ranking or vector learning.

Catalog owns current reading progress. Engagement stores the replicated history/projection that lets interaction workflows avoid synchronous catalog dependency.

## Domain Modules

| Module | Responsibility |
|---|---|
| `rating` | Rating commands, queries, persistence, and `created-rating` publication |
| `reaction` | Reaction lifecycle and `user-reaction-changed` publication |
| `book_snapshot` | Local copy of catalog book state |
| `reading_session_entry` | Kafka-driven reading progress history entries |

## HTTP API Surface

Base paths:

- `/rating`
- `/reactions`

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
```

Reading session entries are currently populated by Kafka consumers, not by a public REST controller.

Through the gateway, engagement routes are exposed under:

```http
/api/v1/engagement/**
```

## Event Contract

Produced topics:

| Topic | Trigger | Consumer |
|---|---|---|
| `created-rating` | New rating | `recommendation-service` |
| `user-reaction-changed` | New or updated reaction | `recommendation-service` |

Consumed topics:

| Topic | Producer | Local use |
|---|---|---|
| `created-book` | Catalog | Create/update local book snapshot |
| `deleted-book` | Catalog | Remove local book snapshot |
| `create-reading-progress` | Catalog | Create reading progress history entry |
| `update-reading-progress` | Catalog contract hardening target | Update reading progress history entry |

Kafka retry and DLT handling are centralized in `share/config/KafkaRetryConfig`.

## Data Ownership

Primary database: `engagement_db`.

Core persisted concepts:

- ratings;
- reactions;
- book snapshots;
- reading session/progress history entries.

Engagement data is behavioral. It should not become the source of truth for catalog metadata or recommendation vectors.

## Security Rules

- JWT authentication required for protected endpoints.
- User context is extracted server-side for write operations.
- Ownership checks apply to update/delete interaction operations.

## Observability and API Docs

| Resource | Path |
|---|---|
| Swagger UI | `/swagger-ui/index.html` |
| OpenAPI JSON | `/v3/api-docs` |
| Health | `/actuator/health` |
| Metrics | `/actuator/metrics` |
| Prometheus | `/actuator/prometheus` |

## Run Locally

Standalone:

```bash
cd engagement-service
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd engagement-service
.\mvnw.cmd spring-boot:run
```

With Docker Compose from the repository root:

```bash
docker-compose up -d engagement-service postgres-engagement kafka
```

## Verify

```powershell
cd engagement-service
.\mvnw.cmd test
```

For the platform event backbone and known Kafka contract hardening work, see the [root README](../README.md).
