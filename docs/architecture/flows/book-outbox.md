---
id: vh-book-outbox
slug: book-outbox
type: sequence-diagram
system: vellumhub
status: implemented-in-repository
related: [vh-service-catalog, vh-service-engagement, vh-service-recommendation, vh-outbox-delivery]
---

# Sequence — Book write, Outbox relay, asynchronous projections

```mermaid
sequenceDiagram
    autonumber
    actor Reader
    participant G as VH-GTW Gateway
    participant C as VH-CAT Catalog
    participant DB as Catalog PostgreSQL
    participant X as Catalog OutboxDispatcher
    participant K as Kafka book-events
    participant E as VH-ENG projection
    participant R as VH-REC projection
    Reader->>G: Authorized Book mutation
    G->>C: Relay bearer request
    C->>DB: BEGIN, persist Book revision + Outbox event
    DB-->>C: COMMIT
    C->>C: afterCommit bump Catalog cache generations (best effort)
    C-->>Reader: HTTP success = local commit only
    loop Poll leased pending rows
        X->>DB: claimNext with lease + fencing token
        DB-->>X: IN_FLIGHT event
        X->>K: Publish canonical BookChangedEvent by bookId
        alt Kafka ACK
            K-->>X: ACK
            X->>DB: Mark PUBLISHED using matching claim token
        else Broker failure / timeout
            X->>DB: Backoff, return to PENDING
        end
    end
    par Consumer projections
        K->>E: Book event, id/revision
        E->>E: Check offset/processed state in transaction
    and
        K->>R: Book event, id/revision
        R->>R: Check id/revision, update local features
    end
    Note over E,R: Consumers may lag. No global transaction or exactly-once claim.
```

**Outbox state machine:** [PENDING → IN_FLIGHT → PUBLISHED](../states/outbox-delivery.md). **Canonical contract:** [Book events](../../BOOK_EVENTS.md) · [Idempotent consumer checkpoints](../../CONSUMER_IDEMPOTENCY.md). **Code:** [writer](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/persistence/JdbcOutboxWriter.java), [store](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/persistence/JdbcOutboxStore.java) and [dispatcher](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/dispatcher/OutboxDispatcher.java).

No event sourcing is implied: Book is canonical in PostgreSQL and the Outbox stores delivery facts. Kafka is asynchronous and legacy topics may still exist during cutover.
