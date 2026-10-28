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
| Engagement events | `created-rating`, `updated-rating`, `user-reaction-changed` |

## What It Does Not Own

- Book source-of-truth data.
- Current reading progress writes.
- User identity or JWT issuing.
- Recommendation ranking or vector learning.

Catalog owns current reading progress. Engagement stores the replicated history/projection that lets interaction workflows avoid synchronous catalog dependency.

## Domain Modules

| Module | Responsibility |
|---|---|
| `rating` | Rating commands, queries, persistence, and rating lifecycle event publication |
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

| Topic | Trigger | Current downstream status |
|---|---|---|
| `created-rating` | Successful new rating | Consumed by `recommendation-service` |
| `updated-rating` | Successful existing-rating update | Consumed by `recommendation-service` |
| `user-reaction-changed` | New or updated reaction | Consumed by `recommendation-service` |

`created-rating` and `updated-rating` carry `eventId`, `occurredAt`, `ratingId`, `userId`, `bookId`, `oldStars`, `newStars`, and `reviewChanged`. Creation uses `oldStars = null` because zero is a valid rating value; update events preserve the real previous and resulting star values.

`user-reaction-changed` carries `eventId`, `occurredAt`, `reactionId`, `userId`, `bookId`, `oldTypeReaction`, and `newTypeReaction`. Creation uses `oldTypeReaction = null`; updates preserve both sides of the transition, including same-value updates for auditability. The legacy `typeReaction` field remains temporarily as a compatibility alias for the resulting state.

For field-level semantics and examples, see [Rating Lifecycle Kafka Contracts](../../docs/RATING_EVENT_CONTRACTS.md) and [Reaction Lifecycle Kafka Contracts](../../docs/REACTION_EVENT_CONTRACTS.md).

Consumed topics:

| Topic | Producer | Local use |
|---|---|---|
| `created-book` | Catalog | Create/update local book snapshot |
| `deleted-book` | Catalog | Remove local book snapshot |
| `created-reading-progress` | Catalog | Create reading progress history entry |
| `updated-reading-progress` | Catalog | Update reading progress history entry |

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

## Replicated reading provenance

`reading_session_entries` is derived history, not the current reading state. Catalog creates
`eventId` and `occurredAt`; consumers persist these as `event_id` and `timestamp`. V5 adds a
nullable event ID and lookup/history indexes. Legacy metadata stays null; processing time
must never substitute for occurrence time. Deploy migrations before the new application.

The [offline exporter](../../training/README.md) labels these records `REPLICATED_HISTORY`
and flags incomplete metadata. It reads current state only from Catalog. Duplicate handling
remains #200; atomic commit/publication remains #201. Metadata alone provides neither guarantee.


## Ordered Kafka failure recovery (#272)

Canonical Book/Reading Progress consumers use bounded retries, service-scoped DLT copies and a persistent PostgreSQL quarantine. Source offsets stay uncommitted when a revision cannot be applied. On recovery, an operator requests original-offset replay and restarts the affected consumer service; the listener marks the quarantine resolved after the record succeeds. Consult [Kafka Recovery Runbook](../../docs/KAFKA_RECOVERY_RUNBOOK.md) for the CLI, audit trail, replay safety and deployment requirements. Legacy DLT replay remains restricted where a stable event identity is unavailable.

## SEC-01 HTTP security baseline

The API remains JWT bearer-based; issue #163 migrates the signature to RS256 with independent public-key validation on this branch (coordinated cutover required). Service security continues to validate tokens independently of the Gateway. Swagger/OpenAPI is opt-in through `VELLUM_DOCS_ENABLED=true` in local Compose; default deployment configuration denies documentation. Health and Prometheus remain unauthenticated at the service HTTP layer for **internal** probe/scrape traffic, so network and Ingress must keep these ports private. CORS uses explicit `CORS_ALLOWED_ORIGINS` and preserves credentialed browser requests for compatibility. JWT Bearer remains the authenticator; any future cookie-session authentication requires separate CSRF review. See [SEC-01 HTTP boundary contract](../../docs/SECURITY_HTTP_BOUNDARY.md) for policy matrix, tests and rollback.

## RS256 public-key validation (issue #163)

This service validates RS256 access tokens through the configured Auth JWKS URI. The decoder explicitly accepts only RS256; invalid, altered, expired, wrong-key, and legacy HS256 bearer JWTs are rejected. It does not hold `JWT_PRIVATE_KEY_LOCATION` and does not contact the User Service for every request. The existing `roles` mapping, resource ownership checks, and HTTP contracts are unchanged. The signing key is owned only by `auth-service`. [Provisioning and coordinated cutover](../../docs/SECURITY_RS256.md).


### Security JWT/JWKS transition (#304, #305)

In draft PR #321, JWT verifiers fetch only trusted public RS256 keys from `JWT_JWKS_URI` and require matching `JWT_ISSUER`, `JWT_AUDIENCE`, `typ=at+jwt`, immutable UUID subject, `client_id`, `jti`, issued/expiry times and signature. Unknown kids and invalid token contexts fail closed. `JWT_CLOCK_SKEW_SECONDS` defaults to 30; key material is not accepted from token-provided URLs. Only `auth-service` signs access tokens and exposes the trusted JWKS; this service is a Resource Server only. Legacy `user_id` is emitted only as a temporary identical compatibility claim; do not use it for new authorization decisions. Read [docs/SECURITY_JWKS_RFC9068.md](../../docs/SECURITY_JWKS_RFC9068.md) for configuration, key rotation, outage behavior and safe cutover. Not yet deployed.
