# Catalog Service

[Architecture card · VH-CAT](../../docs/architecture/services/catalog.md) · [C4 containers](../../docs/architecture/diagrams/c4-containers.md) · [All service codes](../../docs/architecture/services/README.md).

**Current contract:** this is a Resource Server verifying Auth-issued JWTs. Auth, not User, is the identity authority; the repository implementation is not proof of a production rollout.

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

## Busca textual

`GET /api/v1/catalog/books/search?q=ficção+científica&pageNumber=0&pageSize=10`
usa o escopo `catalog:read`. A consulta aceita de 1 a 200 caracteres após
remover espaços das extremidades. O PostgreSQL busca todos os termos em
título, autor, descrição e editora. Os termos são insensíveis a maiúsculas
e preservam os acentos. O ranking prioriza título e autor, com desempate
por data de criação e ID.

A busca retorna o mesmo formato da listagem de livros e uma lista vazia
quando não há correspondência. A migração Flyway V7 cria um índice GIN
parcial para os livros visíveis. A busca consulta o PostgreSQL diretamente
para refletir criações, atualizações e exclusões do catálogo.

Para reproduzir a validação da consulta e do índice em PostgreSQL 15
descartável, execute da raiz do repositório:

```bash
bash infra/scripts/validation/verify-catalog-search.sh
```

O teste usa Docker e Python 3. Aplica as migrações V1–V7 e executa o SQL
do repositório com registros de teste. Também confirma o plano GIN com
6 mil livros adicionais.

## Busca e importação via Google Books

- `GET /api/v1/catalog/books/google?q=Clean+Code&field=TITLE` retorna até dez
  candidatos. Os campos disponíveis são `ALL`, `TITLE`, `AUTHOR` e `ISBN`.
  A rota exige `catalog:read`.
- `POST /api/v1/catalog/books/google` recebe `{"volumeId":"zyTCAlFPjgYC"}`
  e retorna o ID do livro do Catalog com status 201. A rota exige ADMIN
  e `catalog:write`. O serviço consulta o volume selecionado antes de
  persistir o livro e seu evento de criação na mesma transação.

A busca inclui candidatos com `isbn=null`. A importação exige um volume com ISBN. Um volume ausente retorna 404 e falhas
de comunicação com o provedor retornam 502. O fluxo direto por ISBN segue
disponível em `POST /api/v1/catalog/books/isbn`.

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

GET    /book-progress/{bookId}/readings
POST   /book-progress/{bookId}/readings
DELETE /book-progress/{bookId}/readings/{readingId}
PATCH  /book-progress/{bookId}/readings/{readingId}/progress
PATCH  /book-progress/{bookId}/readings/{readingId}/status
PATCH  /book-progress/{bookId}/readings/{readingId}/target
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

## Observability and API Docs

| Resource | Path |
|---|---|
| Swagger UI | `/swagger-ui/index.html` |
| OpenAPI JSON | `/v3/api-docs` |
| Health | `/actuator/health` |
| Metrics | `/actuator/metrics` |
| Prometheus | `/actuator/prometheus` |

## Run Locally

Run the following commands from the **repository root**, where the single Maven Wrapper and reactor POM live.

Standalone:

```bash
./mvnw -pl services/catalog-service -am -DskipTests install
./mvnw -f services/catalog-service/pom.xml spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd -pl services/catalog-service -am -DskipTests install
.\mvnw.cmd -f services/catalog-service/pom.xml spring-boot:run
```

With Docker Compose from the repository root:

```bash
docker compose up -d catalog-service postgres-catalog kafka
```

## Verify

```powershell
.\mvnw.cmd -pl services/catalog-service -am test
```

For full platform topology and event flow, see the [root README](../../README.md).

## Security

Auth is the only issuer. This service validates RS256 `at+jwt` access tokens locally against `JWT_JWKS_URI` (issuer, audience, `typ`, `kid`; `JWT_CLOCK_SKEW_SECONDS` defaults to 30) and never calls Auth or User per request. Each operation needs an explicit OAuth scope plus the USER/ADMIN role ([scope matrix](../../docs/SECURITY_SCOPES_RBAC.md)); ADMIN never implies scopes. Swagger is opt-in (`VELLUM_DOCS_ENABLED`), and health/Prometheus endpoints must stay on the private network. Request path: [authorization sequence](../../docs/architecture/flows/api-request-authorization.md).

- **Ownership:** PUBLIC lists are discoverable; PRIVATE lists are visible only to the owner or members and answer 404 when hidden. Membership directories are readable by owner or member, a single membership by that member, the owner or a list ADMIN. OWNER cannot be assigned through add-member or role update. Reading progress is scoped to the JWT `sub`.
- **Kafka identity:** `catalog_service` produces Book and Reading Progress events only; no consumer-group grant ([Kafka identities](../../docs/KAFKA_SERVICE_IDENTITIES.md)).
