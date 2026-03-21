# 📖 VellumHub

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-Mixed%203.4.x%20%2B%204.0.x-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)](https://www.postgresql.org/)
[![pgvector](https://img.shields.io/badge/pgvector-enabled-blue)](https://github.com/pgvector/pgvector)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

VellumHub is a book-focused microservices platform with event-driven recommendation updates, pgvector similarity search, and local read models for low-latency serving.

This README is intentionally architecture-first and evidence-driven.

---

## Table of Contents

- [System Design (v2.1)](#system-design-v21)
- [Architecture Evolution (v1.0 → v2.0 → v2.1)](#architecture-evolution-v10--v20--v21)
- [Active API Documentation (Swagger/OpenAPI)](#active-api-documentation-swaggeropenapi)
- [AI & Vector Pipeline](#ai--vector-pipeline)
- [Event-Driven Backbone (ECST)](#event-driven-backbone-ecst)
- [Day 2 Operations](#day-2-operations)
  - [Reliability Testing](#reliability-testing)
  - [Resilience & Error Handling](#resilience--error-handling)
  - [Observability](#observability)
- [Database and pgvector Design](#database-and-pgvector-design)
- [Technical Glossary](#technical-glossary)
- [Known Discrepancies & Limitations](#known-discrepancies--limitations)
- [Quick Start](#quick-start)

---

## System Design (v2.1)

```mermaid
graph TB
    subgraph Clients
      U[User]
    end

    subgraph Services
      US[User Service]
      CS[Catalog Service]
      ES[Engagement Service]
      RS[Recommendation Service]
    end

    subgraph Kafka
      B1[created-book]
      B2[updated-book]
      B3[deleted-book]
      R1[created-rating]
    end

    subgraph Databases
      UDB[(user_db)]
      CDB[(catalog_db)]
      EDB[(engagement_db)]
      RDB[(recommendation_db + pgvector)]
    end

    U --> US
    U --> CS
    U --> ES
    U --> RS

    US --> UDB
    CS --> CDB
    ES --> EDB
    RS --> RDB

    CS --> B1
    CS --> B2
    CS --> B3
    ES --> R1

    B1 --> RS
    B2 --> RS
    B3 --> RS
    R1 --> RS
```

The Recommendation Service acts as an event-fed local read model:
- `book_features` for vector search state
- `user_profiles` for preference vectors
- `recommendations` for response metadata

---

## Architecture Evolution (v1.0 → v2.0 → v2.1)

### v1.0
- External ML service in critical recommendation path
- More network hops and operational coupling

### v2.0
- Recommendation compute moved into JVM services + PostgreSQL pgvector
- Historical project docs in this repository report latency movement from ~300–500ms to ~80–120ms in this transition narrative

### v2.1
- Event-Carried State Transfer (ECST) used to keep recommendation state synchronized incrementally
- User interactions feed Kafka topics, and consumers update vectors continuously

```mermaid
graph LR
  V1[v1.0\nExternal ML Service] --> V2[v2.0\npgvector in Recommendation DB]
  V2 --> V21[v2.1\nECST + Local Read Model]
```

---

## Active API Documentation (Swagger/OpenAPI)

Swagger/OpenAPI is configured in all services and exposed publicly via security rules:
`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`.

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| User Service | `http://localhost:8084/swagger-ui/index.html` | `http://localhost:8084/v3/api-docs` |
| Catalog Service | `http://localhost:8081/swagger-ui/index.html` | `http://localhost:8081/v3/api-docs` |
| Engagement Service | `http://localhost:8083/swagger-ui/index.html` | `http://localhost:8083/v3/api-docs` |
| Recommendation Service | `http://localhost:8085/swagger-ui/index.html` | `http://localhost:8085/v3/api-docs` |

---

## AI & Vector Pipeline

### Embedding model and dimensionality

Recommendation uses LangChain4j embeddings with `AllMiniLmL6V2EmbeddingModel`, stored as `vector(384)`:

```java
// recommendation-service/.../share/config/EmbeddingConfig.java
@Bean
public EmbeddingModel embeddingModel() {
    return new AllMiniLmL6V2EmbeddingModel();
}
```

```java
// recommendation-service/.../book_feature/domain/model/BookFeature.java
@Column(name = "embedding", columnDefinition = "vector(384)")
private float[] embedding;
```

```java
// recommendation-service/.../user_profile/domain/model/UserProfile.java
@Column(columnDefinition = "vector(384)")
private float[] profileVector = new float[384];
```

The book embedding provider normalizes vectors to unit length before persistence.

### User profile update logic on new ratings

Profile updates are incremental and normalized:
- Category weight mapping (`DETRACTOR=-5`, `NEUTRAL=1`, `PROMOTER=5`)
- Delta update with learning rate `0.1f`
- L2 normalization after update

```java
int adjustmentWeight = getWeightAdjustment(newStars, oldStars, isNewRating);
this.profileVector[i] += (bookEmbedding[i] * adjustmentWeight * learningRate);
this.normalizeVector(sumOfSquares);
```

### Retrieval path

Candidate retrieval uses cosine-distance operator `<=>`, then re-ranks with popularity blend:

```sql
SELECT
    b.book_id,
    b.popularity_score,
    (b.embedding <=> u.profile_vector) AS vector_dist
...
ORDER BY b.embedding <=> u.profile_vector ASC
LIMIT 200
```

```sql
ORDER BY
    (c.vector_dist * 0.7) + ((1 - COALESCE(c.popularity_score, 0)) * 0.3) ASC
```

---

## Event-Driven Backbone (ECST)

### Learning loop (interaction → event → vector update)

```mermaid
sequenceDiagram
    participant User
    participant ES as Engagement Service
    participant K as Kafka
    participant RS as Recommendation Service
    participant DB as recommendation_db

    User->>ES: Submit rating (bookId, stars)
    ES->>K: Publish created-rating
    K->>RS: Deliver CreatedRatingEvent
    RS->>DB: Update user_profiles.profile_vector (L2 normalized)

    User->>RS: Request recommendations
    RS->>DB: Query book_features with <=> + re-rank
    DB-->>RS: Ranked book IDs
    RS-->>User: Personalized recommendation list
```

### Topic naming (standardized)

Use these topic names consistently:
- `created-book`
- `updated-book`
- `deleted-book`
- `created-rating`

### Internal Contract Variance

There is a real payload variance between producer and consumer contracts for the rating event:

- Engagement producer event field: `mediaId`
- Recommendation consumer event field: `bookId`

This is currently tolerable only because both are UUID in position-compatible event payload mapping, but it is a contract smell and should be aligned.

---

### Observability

- Spring Boot Actuator included in all services
- Health/metrics/prometheus endpoints enabled in service properties
- Health endpoints exposed through security configuration
- Kafka health indicator is enabled (`management.health.kafka.enabled=true`)


---

## Database and pgvector Design

The repository includes explicit Recommendation DB bootstrap SQL:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS book_features (
    book_id UUID PRIMARY KEY,
    embedding vector(384),
    popularity_score DOUBLE PRECISION,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_book_embedding_hnsw
ON book_features
USING hnsw (embedding vector_cosine_ops);
```

Confirmed from code and SQL:
- Index type: `hnsw`
- Operator class: `vector_cosine_ops`
- Similarity operator used in query path: `<=>`

---

## Known Discrepancies & Limitations

### Known Discrepancy 1 — OpenAPI dimensionality text vs runtime implementation

Recommendation runtime implementation is 384-dimensional, but `OpenApiConfig` still documents 15-dimensional genre vectors.

**Exact file to fix:**
- `/home/runner/work/VellumHub/VellumHub/recommendation-service/src/main/java/com/mrs/recommendation_service/share/config/OpenApiConfig.java`

**Strings to update in that file:**
1. `- **Genre-Based Embeddings**: 15-dimensional vectors representing book genres`
2. `- **Vector Dimensions**: 15 (one per genre: Fantasy, Sci-Fi, Horror, Romance, etc.)`
3. `- \`rating-created\`: Update user profile vectors` (should use `created-rating`)

### Known Discrepancy 2 — Internal contract variance

`CreatedRatingEvent` naming mismatch across services:
- Engagement event uses `UUID mediaId`
- Recommendation event uses `UUID bookId`

This should be standardized to a single field name (`bookId`) across producer and consumer contracts.

### Current limitations

- No custom retry/DLT strategy found for Kafka consumers.
- No distributed tracing instrumentation (Sleuth/Zipkin or Micrometer Tracing) found.
- No Testcontainers-based integration test suite found for Kafka + pgvector paths.
- In this runner, Maven tests fail due to Java version mismatch (`release version 21 not supported`).

---

## Quick Start

```bash
docker-compose up -d
```

Service URLs:
- User Service: `http://localhost:8084`
- Catalog Service: `http://localhost:8081`
- Engagement Service: `http://localhost:8083`
- Recommendation Service: `http://localhost:8085`
- Kafka UI: `http://localhost:8090`

---

**VellumHub** — Event-driven, vector-native book recommendations on the JVM.
