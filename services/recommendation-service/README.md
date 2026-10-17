# Recommendation Service

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![pgvector](https://img.shields.io/badge/pgvector-HNSW-blue)](https://github.com/pgvector/pgvector)
[![Kafka](https://img.shields.io/badge/Kafka-consumer-black)](https://kafka.apache.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-Springdoc-brightgreen)](https://springdoc.org/)

The Recommendation Service exists to serve personalized book recommendations from local, event-fed read models.

It is the projection and ranking service: it learns from catalog, user preference, rating, reaction, and progress events, then answers recommendation requests without synchronously calling source-of-truth services in the query path.

## Why This Service Exists

- Maintain local book feature vectors from catalog events.
- Maintain local user profile vectors from preference and engagement events.
- Store denormalized recommendation metadata for response assembly.
- Rank books with PostgreSQL + pgvector instead of an external ML sidecar in the serving path.
- Keep recommendation serving independent from catalog/user/engagement availability at query time.

## What It Owns

| Concern | Owned here |
|---|---|
| Book features | `book_features` with `vector(384)` embeddings and popularity state |
| User profiles | `user_profiles` with profile vectors, engagement score, and interacted book IDs |
| Recommendation read model | `recommendations` denormalized response data |
| Ranking | ANN candidate retrieval and semantic/popularity re-ranking |
| Database | `recommendation_db` with pgvector |
| Kafka consumers | Projection and learning updates from upstream events |

## What It Does Not Own

- Book source-of-truth data.
- User identity or authentication source of truth.
- Rating/reaction source-of-truth data.
- Current reading progress writes.
- Gateway routing or rate limiting.

Feign-related configuration still exists as cleanup debt, but recommendation serving is local/read-model based and has no synchronous source-service call in the query path.

## Domain Modules

| Module | Responsibility |
|---|---|
| `book_feature` | Embedding generation, feature persistence, ANN ID retrieval |
| `user_profile` | Profile vector creation and incremental learning from events |
| `recommendation` | Recommendation retrieval, pagination, mapping, and response assembly |

## HTTP API Surface

Base path:

- `/recommendations`

Endpoint:

```http
GET /recommendations?limit=10&offset=0
```

The authenticated user ID is resolved from JWT/security context and used to rank personalized results.

Through the gateway, recommendation routes are exposed under:

```http
/api/v1/recommendations/**
```

## Event Contract

Canonical versioned topics:

| Topic | Producer | Local effect / checkpoint |
|---|---|---|
| `book-events` | Catalog | Book metadata/features via `book_event_offsets` and `processed_events` |
| `reading-progress-events` | Catalog | User progress delta via `reading_progress_offsets` and `processed_events` |
| `engagement.ratings.v1` | Engagement | Rating lifecycle via `engagement_event_offsets` / `processed_engagement_events` |
| `engagement.reactions.v1` | Engagement | Reaction transitions via the same Engagement checkpoints |
| `created-user-preference` | User | Cold-start user preference updates (outside #200's canonical versioned streams) |

Older topics (`created/updated/deleted-book`, `created/updated-reading-progress`, `created/updated-rating` and `user-reaction-changed`) have compatibility listeners. They do not share the canonical checkpoint guarantees; keep them only during a coordinated migration and disable them with `KAFKA_LEGACY_CONSUMERS_ENABLED=false` after draining. The current default is `true` to preserve pre-cutover behavior. See [Consumer Idempotency](../../docs/CONSUMER_IDEMPOTENCY.md) before enabling the canonical writers.

Rating updates within the same rating category produce a zero adjustment and leave the profile vector unchanged. Reaction updates follow the same transition rule: Recommendation applies `weight(new) - weight(old)`, while reaction creation applies the full resulting weight. Same-value reaction updates therefore produce zero profile drift.

See [Reaction Lifecycle Kafka Contracts](../../docs/REACTION_EVENT_CONTRACTS.md) for the enriched reaction payload, legacy compatibility, and delivery-guarantee boundary.

Legacy retry/DLT handling is configured in `share/kafka/config/KafkaRetryConfig`. Canonical ordered consumers use blocking retries until the operational retry/DLT/replay policy is implemented in #272.

## Vector Pipeline and Ranking

| Step | Implementation |
|---|---|
| Embedding model | LangChain4j `AllMiniLmL6V2EmbeddingModel` |
| Vector size | 384 dimensions |
| Book vector storage | `book_features.embedding vector(384)` |
| User vector storage | `user_profiles.profile_vector vector(384)` |
| Normalization | L2 normalization before persistence/use |
| ANN index | HNSW with `vector_cosine_ops` |
| Candidate pool | 200 nearest candidates |
| Re-ranking | 70% semantic distance, 30% popularity signal |
| Fallback | Popularity ranking when no profile exists |

The pgvector extension and HNSW indexes are managed through Flyway migrations.

## Data Ownership

Primary database: `recommendation_db` (PostgreSQL + pgvector).

Core read models:

- `book_features`;
- `user_profiles`;
- `recommendations`.

Canonical ingestion state: `processed_events`, `book_event_offsets`, `reading_progress_offsets`, `processed_engagement_events` and `engagement_event_offsets`. Event identity and checkpoint updates commit with their local effects.

These are derived read models. Upstream services remain the source of truth for books, users, ratings, reactions, and current reading progress.

## Security and Observability

| Resource | Path |
|---|---|
| Swagger UI | `/swagger-ui/index.html` |
| OpenAPI JSON | `/v3/api-docs` |
| Health | `/actuator/health` |
| Metrics | `/actuator/metrics` |
| Prometheus | `/actuator/prometheus` |

The public recommendation endpoint is JWT-protected.

## Run Locally

Standalone from the repository root:

```bash
cd services/recommendation-service
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd services/recommendation-service
.\mvnw.cmd spring-boot:run
```

With Docker Compose from the repository root:

```bash
docker-compose up -d recommendation-service postgres-recommendation kafka
```

## Verify

Module tests on Windows:

```powershell
cd services/recommendation-service
.\mvnw.cmd test
```

The failure-sensitive Kafka integration suite uses real Kafka and PostgreSQL/pgvector Testcontainers. No local Kafka or PostgreSQL installation is required, but Docker must be running.

From the repository root:

```powershell
docker info
mvn -pl services/recommendation-service -am -Dgroups=distributed test
```

The distributed suite covers the legacy `created-book` retry/DLT boundary, rating and reaction lifecycle tests, and the canonical #200 idempotency scenarios (replay, concurrency, rollback and real Kafka redelivery).

See [Distributed Integration Testing](../../docs/DISTRIBUTED_TESTING.md) for infrastructure boundaries, CI budget, extension rules, and the planned E2E boundary.

For platform topology and broader hardening work, see the [root README](../../README.md).

## Online updates and offline training inputs

Online profiles consume Catalog's reading-progress events directly; Engagement is not an
intermediate publisher or synchronous dependency. `eventId`/`occurredAt` enrich provenance
without changing profile weights. Canonical consumers fail and retry when required BookFeature data is missing; legacy consumers may still warn and skip during migration.

The separate [offline exporter](../../training/README.md) reads canonical Catalog state and
Engagement feedback/history into versioned facts for #193. Replicated history never substitutes
for current Catalog state. This is distinct from the synthetic evaluation harness.


## Ordered Kafka failure recovery (#272)

Canonical Book/Reading Progress/Rating/Reaction consumers use bounded retries, service-scoped DLT copies and a persistent PostgreSQL quarantine. Source offsets stay uncommitted when a revision cannot be applied. On recovery, an operator requests original-offset replay and restarts the affected consumer service; the listener marks the quarantine resolved after the record succeeds. Consult [Kafka Recovery Runbook](../../docs/KAFKA_RECOVERY_RUNBOOK.md) for the CLI, audit trail, replay safety and deployment requirements. Legacy DLT replay remains restricted where a stable event identity is unavailable.
