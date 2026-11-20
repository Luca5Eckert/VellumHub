---
id: vh-c4-catalog-components
slug: c4-catalog-components
type: c4-component-diagram
c4_level: 3
system: vellumhub
container_code: VH-CAT
status: implemented-in-repository
related: [vh-service-catalog, vh-catalog-cache, vh-book-outbox, vh-outbox-delivery]
---

# C4 L3 — Catalog read cache and canonical write components

The Catalog component boundary distinguishes a **read optimization** (Redis DTO cache) from the **canonical write path** (Book aggregate + PostgreSQL Outbox). Versioned cache generations are bumped **after commit**; Kafka is not a prerequisite for an HTTP write transaction to commit.

```mermaid
C4Component
    title VH-CAT — Book HTTP reads, versioned cache and Outbox writes
    Container_Boundary(cat, "VH-CAT · Catalog service") {
        Component(sec, "SecurityFilterChain", "Spring Security", "Validates JWT scope/role before handlers")
        Component(read, "BookController + GET handlers", "Spring MVC", "Detail and deterministic page reads")
        Component(cache, "BookReadCache", "Redis cache-aside", "DTO / generation / TTL / bounded miss")
        Component(repo, "Book repository", "Spring Data JPA", "Canonical Book data")
        Component(write, "Book mutation handlers", "Application", "Create, ISBN, update, delete, cover")
        Component(outbox, "OutboxBookChangeWriter", "Application port", "Appends committed Book fact")
        Component(relay, "OutboxDispatcher", "Scheduled worker", "Leased, fenced Kafka delivery")
    }
    ContainerDb(pg, "Catalog PostgreSQL", "PostgreSQL", "Books and Outbox")
    Container(redis, "Domain cache Redis", "Redis", "Disposable DTOs and generation markers")
    Container(kafka, "Kafka", "Broker", "book-events / reading-progress-events")
    Rel(sec, read, "Authorizes GET")
    Rel(sec, write, "Authorizes mutations")
    Rel(read, cache, "Read-through DTO")
    Rel(cache, redis, "GET/SET TTL + generations")
    Rel(cache, repo, "Miss / Redis failure", "JPA")
    Rel(repo, pg, "Books")
    Rel(write, repo, "Persist Book", "JPA transaction")
    Rel(write, outbox, "Record change", "Same transaction")
    Rel(outbox, pg, "Append outbox row", "JDBC")
    Rel(outbox, cache, "Invalidate after commit", "Port")
    Rel(relay, pg, "Lease, publish, retry", "JDBC")
    Rel(relay, kafka, "Emit committed facts", "Kafka")
```

**Clarification:** the component layout summarizes interactions; actual mutation handlers may invoke the domain `BookChangeWriter` abstraction rather than calling `OutboxBookChangeWriter` directly. The cache implements `BookCacheInvalidationPort`, which the writer depends on (no application → infrastructure import). The after-commit callback does not roll back a Book mutation when Redis fails. A stale in-flight request may return an older snapshot; the generation token prevents stale fills from populating a **new** generation.

| Invariant | Implementation / evidence |
| --- | --- |
| JWT before cache | [SecurityConfig](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/share/security/SecurityConfig.java) |
| Stable pagination | [GetAllBooksHandler](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/book/application/handler/get/GetAllBooksHandler.java) |
| Cache and bulkhead | [BookReadCache](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/book/infrastructure/cache/BookReadCache.java) |
| Outbox transaction | [OutboxBookChangeWriter](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/book/application/event/OutboxBookChangeWriter.java) |
| Delivery statuses | [JdbcOutboxStore](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/persistence/JdbcOutboxStore.java) |
| Cache hardening | [Runbook](../../CATALOG_REDIS_CACHE.md) |

Detailed flows: [cache sequence](../flows/catalog-cache.md) · [Book Outbox](../flows/book-outbox.md) · [Outbox states](../states/outbox-delivery.md).
