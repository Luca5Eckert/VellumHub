# Catalog Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-producer-black)](https://kafka.apache.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Springdoc-brightgreen)](https://springdoc.org/)

The Catalog Service exists to be the source of truth for books and current reading state.

It owns catalog writes and publishes book/progress events so downstream services can build local read models without sharing the catalog database.

## Why This Service Exists

- Own book records, metadata, covers, and ISBN-based creation.
- Manage community book requests and admin approval.
- Own user book lists and memberships.
- Own current reading progress/status for a user and a book.
- Emit catalog events used by recommendation and engagement projections.

## What It Owns

| Concern | Owned here |
|---|---|
| Books | CRUD, search/pagination, ISBN creation, bulk lookup, covers |
| Book requests | User submission and admin approval |
| Book lists | User-curated lists, members, roles, add/remove books |
| Current reading state | Reading status, current page, reading list |
| Database | `catalog_db` |
| Catalog events | Book lifecycle and reading progress events |

## What It Does Not Own

- User identity or JWT issuing.
- Ratings and reactions.
- Recommendation ranking or user profile vectors.

## Domain Modules

| Module | Responsibility |
|---|---|
| `book` | Book aggregate, CRUD, ISBN provider integration, cover handling |
| `book_request` | Community request and approval workflow |
| `book_list` | Lists, filters, member roles, and book membership |
| `book_progress` | Current reading status and current-page lifecycle |
| `files/books` | Local cover file access |

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

GET    /files/books/{filename}
```

Through the gateway, catalog routes are exposed under:

```http
/api/v1/catalog/**
```

## Event Contract

Produced Kafka topics:

| Topic | Trigger | Downstream use |
|---|---|---|
| `created-book` | Book creation or request approval | Recommendation book features, engagement book snapshot |
| `updated-book` | Book update | Recommendation feature/metadata refresh |
| `deleted-book` | Book deletion | Recommendation cleanup, engagement snapshot cleanup |
| `created-reading-progress` | Reading status/progress initialization | Engagement reading history |
| `updated-reading-progress` | Current page/progress update | Recommendation profile learning |

Topic names and event payloads are defined in `lib/kafka-contracts`.

## Data Ownership

Primary database: `catalog_db`.

Core aggregates:

- books;
- genres;
- book requests;
- book lists;
- list memberships;
- current book progress.

Downstream services should consume catalog truth through HTTP or Kafka events, not through shared database access.

## Security Rules

- JWT required for protected catalog flows.
- Admin-only operations for catalog mutation and request approval.
- User-scoped access for lists, memberships, and reading progress.

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
cd catalog-service
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd catalog-service
.\mvnw.cmd spring-boot:run
```

With Docker Compose from the repository root:

```bash
docker-compose up -d catalog-service postgres-catalog kafka
```

## Verify

```powershell
cd catalog-service
.\mvnw.cmd test
```

For full platform topology and event flow, see the [root README](../README.md).
