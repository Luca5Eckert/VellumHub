---
id: vh-outbox-delivery
slug: outbox-delivery
type: state-machine
system: vellumhub
container_code: VH-CAT
status: implemented-in-repository
related: [vh-book-outbox, vh-service-catalog]
---

# State machine — Catalog Outbox event delivery

**This is a persisted state machine**, derived from `JdbcOutboxStore`: exactly `PENDING`, `IN_FLIGHT` and `PUBLISHED`. A failed Kafka send does **not** persist a terminal `FAILED` state; it returns to `PENDING` with an error, backoff timestamp and incremented attempt count. Claim token and lease fence concurrent dispatchers.

```mermaid
stateDiagram-v2
    [*] --> PENDING: DB transaction inserts event
    PENDING --> IN_FLIGHT: claimNext / token + lease + attempts++
    IN_FLIGHT --> PUBLISHED: Kafka ACK / fenced published(eventId, token)
    IN_FLIGHT --> PENDING: Error / failed(eventId, token) + bounded backoff
    IN_FLIGHT --> IN_FLIGHT: Lease expired / new claim token
    PUBLISHED --> [*]: Terminal delivery status (row remains stored)
    note right of IN_FLIGHT
      A lost ACK can cause redelivery.
      Consumers deduplicate by eventId
      and aggregate revision/checkpoint.
    end note
```

| Transition | Guard / failure mode | Implementation |
| --- | --- | --- |
| Insert → PENDING | Committed canonical aggregate + Outbox record; rollback inserts neither | [JdbcOutboxWriter](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/persistence/JdbcOutboxWriter.java) |
| PENDING → IN_FLIGHT | Due `next_attempt_at` and no earlier un-published aggregate revision; row `SKIP LOCKED` | [claimNext](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/persistence/JdbcOutboxStore.java) |
| IN_FLIGHT → PUBLISHED | Kafka ACK; matching `claim_token` prevents stale workers finalizing | [OutboxDispatcher](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/dispatcher/OutboxDispatcher.java) |
| IN_FLIGHT → PENDING | Failure; exponential bounded backoff (at most 300 s) | [OutboxDispatcher](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/dispatcher/OutboxDispatcher.java) |
| IN_FLIGHT → IN_FLIGHT | Lease expiration allows reclaim with a new token | [JdbcOutboxStore](../../../services/catalog-service/src/main/java/com/vellumhub/catalog_service/module/outbox/infrastructure/persistence/JdbcOutboxStore.java) |

No globally exactly-once delivery is claimed. Kafka ACK plus an unsuccessful PUBLISHED update can lead to retry and duplicate downstream facts, so consumer idempotency remains necessary. See [sequence diagram](../flows/book-outbox.md) and [consumer contracts](../../CONSUMER_IDEMPOTENCY.md).
