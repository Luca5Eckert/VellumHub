# 📚 Catalog Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black)](https://kafka.apache.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0-brightgreen)](https://swagger.io/specification/)

The **Catalog Service** owns the full catalog domain: books, requests, user book lists/memberships, and reading progress.

It is a write-source service that emits catalog and progress events consumed by downstream read models.

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

- System of record for catalog entities
- Publishes book lifecycle events (`created-book`, `updated-book`, `deleted-book`)
- Publishes reading interaction updates (`updated-progress`)
- Supplies data mirrored by Recommendation and Engagement services through ECST

---

## Domain Modules

| Module | Responsibility |
|---|---|
| `book` | Core CRUD, ISBN-based creation, bulk retrieval, cover upload/retrieval |
| `book_request` | Community submission and admin approval flow |
| `book_list` | Curated lists, filters, and add/remove books |
| `book_list/membership` | Membership and role management inside lists |
| `book_progress` | User reading status/page progress lifecycle |

---

## HTTP API Surface

Base paths:

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

Produced Kafka topics:

- `created-book`
- `updated-book`
- `deleted-book`
- `updated-progress`

Downstream usage:

- Recommendation Service: update `book_features`, profile learning inputs, recommendation denormalized state
- Engagement Service: update local `book_snapshot`

---

## Data Ownership

Primary DB: `catalog_db`.

Core aggregates include books, requests, lists/memberships, and user progress tracking. Other services should consume catalog truth via API/events (no shared DB contracts).

---

## Security Rules

- JWT required for protected endpoints
- `ADMIN` required for mutating book catalog and request approval
- User-scoped flows for list membership and reading progress

---

## Observability and API Docs

- Actuator: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI: `/v3/api-docs`

---

## Quick Start

### Run locally

```bash
cd catalog-service
./mvnw spring-boot:run
```

### Run via Docker Compose

```bash
docker-compose up -d catalog-service
```

### Configuration highlights

- `SERVER_PORT` (default `8080`)
- `SPRING_DATASOURCE_*`
- `KAFKA_BOOTSTRAP_SERVERS`
- `JWT_KEY`

---

See [root README](../README.md) for cross-service topology.
