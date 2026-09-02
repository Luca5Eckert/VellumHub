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

Consumed topics:

| Topic | Producer | Local effect |
|---|---|---|
| `created-book` | Catalog | Create book feature and recommendation metadata |
| `updated-book` | Catalog | Refresh book feature and recommendation metadata |
| `deleted-book` | Catalog | Remove local recommendation state for the book |
| `created-rating` | Engagement | Adjust user profile vector from rating signal |
| `user-reaction-changed` | Engagement | Adjust user profile vector from reaction signal |
| `created-user-preference` | User | Seed or update cold-start profile vector |
| `created-reading-progress` | Catalog | Adjust user profile from new progress event |
| `updated-reading-progress` | Catalog | Adjust user profile from progress update |

Retry and Dead Letter Topic handling are centralized in `share/kafka/config/KafkaRetryConfig`.

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

Core tables:

- `book_features`;
- `user_profiles`;
- `recommendations`.

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

The distributed suite currently proves the `created-book` happy path and the three-attempt retry -> DLT failure path. Idempotency and transactional outbox tests are intentionally deferred until those production guarantees exist.

See [Distributed Integration Testing](../../docs/DISTRIBUTED_TESTING.md) for infrastructure boundaries, CI budget, extension rules, and the planned E2E boundary.

For platform topology and broader hardening work, see the [root README](../../README.md).
