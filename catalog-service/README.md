# 📚 Catalog Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x%20%2F%204.0.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black)](https://kafka.apache.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0-brightgreen)](https://swagger.io/specification/)

The **Catalog Service** owns the platform catalog domain: books, book requests, user book lists, memberships, and reading progress.

It is the write-source for catalog state and publishes domain events consumed by other services (especially Recommendation and Engagement), following the architecture described in the [root README](../README.md).

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

- **Domain owner** for catalog entities and workflows
- **Producer** of catalog lifecycle events (`created-book`, `updated-book`, `deleted-book`)
- **Producer** of reading progress interactions (`updated-progress`)
- **Source of truth** for recommendation metadata replicated through ECST in downstream services

---

## Module Map

| Module | Responsibility |
|---|---|
| `book` | Book CRUD, ISBN creation, bulk fetch, cover upload/retrieval |
| `book_request` | Community request flow and admin approval |
| `book_list` | User-curated lists, list memberships, add/remove books |
| `book_progress` | Per-user status/progress and reading list |

---

## HTTP API Surface

Base paths exposed by this service:

- `/books`
- `/book-requests`
- `/book-list`
- `/book-list/membership`
- `/book-progress`
- `/files/books`

Representative endpoints:

```http
POST   /books
POST   /books/isbn
GET    /books
GET    /books/{id}
PUT    /books/{id}
DELETE /books/{id}
POST   /books/bulk
POST   /books/{id}/cover
GET    /books/{id}/cover
POST   /books/covers/bulk

POST   /book-requests
POST   /book-requests/{requestId}/approve
GET    /book-requests

POST   /book-list
GET    /book-list
GET    /book-list/{bookListId}
PUT    /book-list/{bookListId}
DELETE /book-list/{bookListId}
POST   /book-list/{bookListId}/books/{bookId}
DELETE /book-list/{bookListId}/books/{bookId}

POST   /book-list/membership/{bookListId}
GET    /book-list/membership
GET    /book-list/membership/{memberId}
PUT    /book-list/membership/{memberId}/role
DELETE /book-list/membership/{bookListId}/{memberId}

POST   /book-progress/{bookId}/status
PUT    /book-progress/{bookId}/progress
DELETE /book-progress/{bookId}
GET    /book-progress/reading-list
```

---

## Event Contract

Produced topics:

- `created-book`
- `updated-book`
- `deleted-book`
- `updated-progress`

These events are consumed by Recommendation Service (vector state + profile learning) and by Engagement Service (`book_snapshot`) for local read models.

---

## Data Ownership

Main database: `catalog_db`.

Core aggregates include:

- `books`
- `book_requests`
- `book_lists`
- `book_list_memberships`
- `book_progress`

The service is authoritative for catalog consistency; other services should rely on events or API contracts rather than direct data coupling.

---

## Security and Observability

- JWT-protected endpoints via Spring Security (`bearerAuth`)
- Role-sensitive operations (`ADMIN`) for catalog mutation/approval
- Actuator enabled: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Swagger/OpenAPI: `/swagger-ui/index.html`, `/v3/api-docs`

---

## Quick Start

### Run locally

```bash
cd catalog-service
./mvnw spring-boot:run
```

### Run via Docker Compose (from repo root)

```bash
docker-compose up -d catalog-service
```

### Default local access

- Service: `http://localhost:8081`
- Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- OpenAPI: `http://localhost:8081/v3/api-docs`

---

For cross-service architecture (Gateway, ECST, pgvector pipeline, retry/DLT), see [VellumHub root README](../README.md).
