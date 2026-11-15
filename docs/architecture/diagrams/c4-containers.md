---
id: vh-c4-containers
slug: c4-containers
type: c4-container-diagram
c4_level: 2
system: vellumhub
status: implemented-in-repository
related: [vh-c4-context, vh-services, vh-book-outbox, vh-recommendation-feedback]
---

# C4 L2 — Backend containers and owned data

**Implemented topology, not a production deployment map.** Five logical PostgreSQL databases remain private to their owners (Auth, User, Catalog, Engagement and Recommendation). Kafka carries integration facts. Gateway rate limiting and domain response caching use **different Redis instances**; local Catalog and Recommendation share one disposable domain-cache Redis with separate key namespaces.

```mermaid
C4Container
    title VellumHub — six service containers and integration boundaries
    Person(reader, "Reader", "Browser / API client")
    System_Ext(google, "Google OIDC", "Optional federation")
    System_Ext(books, "Google Books", "ISBN metadata")
    System_Boundary(vh, "VellumHub") {
        Container(auth, "VH-AUT · Auth", "Java / Spring Authorization Server", "OAuth/OIDC and canonical JWT issuer")
        Container(gateway, "VH-GTW · Gateway", "Spring Cloud Gateway", "API entry, bearer validation and rate limits")
        Container(user, "VH-USR · User", "Spring Boot", "Profile and preferences")
        Container(catalog, "VH-CAT · Catalog", "Spring Boot", "Canonical Book and reading progress")
        Container(eng, "VH-ENG · Engagement", "Spring Boot", "Ratings, reactions and progress history")
        Container(rec, "VH-REC · Recommendation", "Spring Boot / pgvector", "Local vectors, ranker and read model")
        ContainerDb(authdb, "Auth DB", "PostgreSQL", "Identity, grants and sessions")
        ContainerDb(userdb, "User DB", "PostgreSQL", "Profiles and preferences")
        ContainerDb(catdb, "Catalog DB", "PostgreSQL", "Books, progress and Outbox")
        ContainerDb(engdb, "Engagement DB", "PostgreSQL", "Feedback, history and Outbox")
        ContainerDb(recdb, "Recommendation DB", "PostgreSQL + pgvector", "Derived features, profiles and checkpoints")
        Container(kafka, "Kafka", "Broker", "Committed integration events")
        Container(redisEdge, "Gateway Redis", "Redis", "Rate limiting only")
        Container(redisDomain, "Domain cache Redis", "Redis", "Optional Catalog/Recommendation DTO caches")
    }
    Rel(reader, auth, "Login / PKCE / browser session", "OAuth2/OIDC")
    Rel(reader, gateway, "Domain API requests", "Bearer HTTP")
    Rel(auth, google, "Optional identity federation", "OIDC")
    Rel(auth, user, "Idempotent profile provisioning", "Private HTTP")
    Rel(auth, authdb, "Writes / reads", "JDBC")
    Rel(gateway, redisEdge, "Rate-limit counters", "Redis")
    Rel(gateway, user, "Routes profile requests", "HTTP")
    Rel(gateway, catalog, "Routes book and progress", "HTTP")
    Rel(gateway, eng, "Routes ratings and reactions", "HTTP")
    Rel(gateway, rec, "Routes recommendation GET", "HTTP")
    Rel(user, userdb, "Owns", "JPA")
    Rel(catalog, catdb, "Owns + Outbox", "JPA / JDBC")
    Rel(eng, engdb, "Owns + Outbox", "JPA / JDBC")
    Rel(rec, recdb, "Owns projections", "JPA / pgvector")
    Rel(catalog, books, "ISBN lookup", "HTTPS")
    Rel(user, kafka, "Preferences", "Kafka")
    Rel(catalog, kafka, "Book and progress facts", "Kafka / Outbox")
    Rel(eng, kafka, "Rating and reaction facts", "Kafka / Outbox")
    Rel(kafka, eng, "Book snapshots / progress history", "Kafka")
    Rel(kafka, rec, "Preference, Book, feedback and progress", "Kafka")
    Rel(catalog, redisDomain, "Optional book DTO cache", "Redis")
    Rel(rec, redisDomain, "Optional ranking DTO cache", "Redis")
```

## Boundary checklist

| Rule | Code-backed interpretation |
| --- | --- |
| Identity issuer | [VH-AUT](../services/auth.md) is sole token issuer; Gateway and domain services verify bearer locally |
| Database ownership | No service directly joins a different service's application PostgreSQL tables |
| Recommendation autonomy | [VH-REC](../services/recommendation.md) serves from its own projection; no synchronous User/Catalog/Engagement lookup on GET |
| Eventual consistency | Canonical facts are persisted with Outbox and delivered through Kafka; a successful producer response does **not** mean all projections converged |
| Redis isolation | Gateway limiter Redis is distinct from local domain DTO cache Redis; DB remains source of truth |
| Environment | Local Compose/Kind cache flags ON; prod overlay OFF until provisioned and verified |

Follow deeper: [Catalog C4 L3](c4-catalog-components.md) · [Book event sequence](../flows/book-outbox.md) · [Recommendation sequence](../flows/recommendation-feedback.md) · [Service codes](../services/README.md).
