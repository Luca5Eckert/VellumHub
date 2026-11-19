---
id: vh-ordered-consumer
slug: ordered-consumer
type: sequence-diagram
system: vellumhub
status: implemented-in-repository
related: [vh-kafka-quarantine, vh-book-outbox, vh-recommendation-feedback, vh-service-engagement, vh-service-recommendation]
---

# Sequence — ordered Kafka consumer with checkpoint and quarantine

Engagement and Recommendation apply canonical facts (Book, ReadingProgress, Rating, Reaction) **per aggregate in revision order**. The projection change, the checkpoint and the processed-event record commit in one PostgreSQL transaction; the Kafka offset is committed only after that. Delivery is at-least-once, so duplicates are expected and absorbed.

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka topic (partition)
    participant L as Ordered listener container
    participant UC as Apply use case / EngagementEventProcessor
    participant DB as Consumer PostgreSQL
    participant R as OrderedKafkaRecovery
    participant DLT as Service DLT topic
    K->>L: Record (key = aggregate ID)
    L->>UC: apply(fact)
    UC->>DB: BEGIN, lock checkpoint row (SELECT … FOR UPDATE)
    alt revision <= last applied
        UC->>DB: Same eventId already processed?
        alt yes
            UC-->>L: DUPLICATE (no side effect)
        else different identity
            UC-->>L: IllegalStateException (revision collision)
        end
    else revision != last + 1, or first fact is not CREATED / BASELINE
        UC-->>L: IllegalStateException (revision gap)
    else next revision
        UC->>DB: Insert processed eventId, apply projection delta
        UC->>DB: Advance checkpoint (compare-and-set on last), COMMIT
        UC-->>L: APPLIED
        L->>K: Commit offset
    end
    opt Exception
        L->>L: Retry with fixed back-off (default 1 s × 3), invalid input is not retried
        L->>R: Retries exhausted
        R->>DLT: Publish diagnostic copy + source topic/partition/offset headers (acks=all)
        R->>DB: Upsert kafka_consumer_quarantine = BLOCKED
        R->>L: Pause partition, source offset stays uncommitted
    end
```

| Step | Implementation |
| --- | --- |
| Book revision rule (`verifyRevisionContinuity`) | Engagement [ApplyBookEventUseCase](../../../services/engagement-service/src/main/java/com/vellumhub/engagement_service/module/book_event/application/use_case/ApplyBookEventUseCase.java) · Recommendation [ApplyBookEventUseCase](../../../services/recommendation-service/src/main/java/com/vellumhub/recommendation_service/module/book_event/application/use_case/ApplyBookEventUseCase.java) |
| Rating/Reaction checkpoint, duplicate and collision rules | [EngagementEventProcessor](../../../services/recommendation-service/src/main/java/com/vellumhub/recommendation_service/module/user_profile/infrastructure/consumer/EngagementEventProcessor.java) |
| Retry, DLT, quarantine, partition pause | [OrderedKafkaRecovery](../../../services/recommendation-service/src/main/java/com/vellumhub/recommendation_service/share/kafka/recovery/OrderedKafkaRecovery.java) (identical copy in Engagement, guarded by `tools/architecture/test_ordered_recovery_parity.py`) |
| Real Kafka + PostgreSQL proof | `ConsumerIdempotencyDistributedTest` |

The DLT is diagnostic only: records are never re-appended to the tail of an ordered topic. Recovery resumes from the **original** offset — see the [quarantine state machine](../states/kafka-quarantine.md) and the [recovery runbook](../../KAFKA_RECOVERY_RUNBOOK.md).
