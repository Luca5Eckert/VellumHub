# Catalog Service

**SEC-16 (#317, PR #336):** The default backend runtime in this branch validates Auth-issued access tokens, replacing the earlier opt-in OIDC integration path. Production rollout and full integration checks are not yet confirmed; no frontend migration is required for this service.

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

## SEC-01 HTTP security baseline

The API remains JWT bearer-based; issue #163 migrates the signature to RS256 with independent public-key validation on this branch (coordinated cutover required). Service security continues to validate tokens independently of the Gateway. Swagger/OpenAPI is opt-in through `VELLUM_DOCS_ENABLED=true` in local Compose; default deployment configuration denies documentation. Health and Prometheus remain unauthenticated at the service HTTP layer for **internal** probe/scrape traffic, so network and Ingress must keep these ports private. CORS uses explicit `CORS_ALLOWED_ORIGINS` and preserves credentialed browser requests for compatibility. JWT Bearer remains the authenticator; any future cookie-session authentication requires separate CSRF review. See [SEC-01 HTTP boundary contract](../../docs/SECURITY_HTTP_BOUNDARY.md) for policy matrix, tests and rollback.

## RS256 public-key validation (issue #163)

This service validates RS256 access tokens through the configured Auth JWKS URI. The decoder explicitly accepts only RS256; invalid, altered, expired, wrong-key, and legacy HS256 bearer JWTs are rejected. It does not hold `JWT_PRIVATE_KEY_LOCATION` and does not contact the User Service for every request. The existing `roles` mapping, resource ownership checks, and HTTP contracts are unchanged. The signing key is owned only by `auth-service`. [Provisioning and coordinated cutover](../../docs/SECURITY_RS256.md).


### Security JWT/JWKS transition (#304, #305)

PR #321 (merged) introduced JWT verifiers that fetch only trusted public RS256 keys from `JWT_JWKS_URI` and require matching `JWT_ISSUER`, `JWT_AUDIENCE`, `typ=at+jwt`, immutable UUID subject, `client_id`, `jti`, issued/expiry times and signature. Unknown kids and invalid token contexts fail closed. `JWT_CLOCK_SKEW_SECONDS` defaults to 30; key material is not accepted from token-provided URLs. Only `auth-service` signs access tokens and exposes the trusted JWKS; Catalog is a Resource Server only. Legacy `user_id` is emitted only as a temporary identical compatibility claim; do not use it for new authorization decisions. Read [docs/SECURITY_JWKS_RFC9068.md](../../docs/SECURITY_JWKS_RFC9068.md) for configuration, key rotation, outage behavior and safe cutover. Not yet deployed.

## SEC-10 — explicit OAuth capabilities

Domain HTTP operations require an Auth-issued human access token with an explicit
scope and the required USER/ADMIN role. ADMIN never implies scopes, and machine
grants cannot impersonate a human. See [the endpoint matrix, configuration and
rollback guide](../../docs/SECURITY_SCOPES_RBAC.md). OpenAPI documents each scope.
Fresh tokens must request the relevant capabilities; `openid profile` alone is
insufficient. Ownership rules remain separate.
