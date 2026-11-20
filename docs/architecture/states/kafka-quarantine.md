---
id: vh-kafka-quarantine
slug: kafka-quarantine
type: state-machine
system: vellumhub
status: implemented-in-repository
related: [vh-ordered-consumer, vh-service-engagement, vh-service-recommendation]
---

# State machine — ordered consumer quarantine

**Persisted** in `kafka_consumer_quarantine.status` (`CHECK status IN ('BLOCKED','RETRY_REQUESTED','RESOLVED')`), one row per consumer group + source topic/partition/offset. A non-resolved row keeps its partition from progressing, so no later revision of the same aggregate is applied out of order.

```mermaid
stateDiagram-v2
    [*] --> BLOCKED: retries exhausted / DLT copy acked, row upserted, partition paused
    BLOCKED --> RETRY_REQUESTED: operator resume after fix (scripts/kafka-recovery.py)
    RETRY_REQUESTED --> RESOLVED: record at that offset processed successfully
    RETRY_REQUESTED --> BLOCKED: fails again / upsert BLOCKED, attempts updated
    RESOLVED --> [*]
    note left of BLOCKED
      On every rebalance the partition is paused again.
      If the DLT publish or the DB write fails, no row
      is written and the record is retried by Kafka.
    end note
    note right of RETRY_REQUESTED
      On consumer restart the partition
      seeks to the original offset.
    end note
```

| Transition | Guard | Implementation |
| --- | --- | --- |
| → BLOCKED | `DefaultErrorHandler` exhausted, or non-retryable (`IllegalArgumentException`, `DeserializationException`, `DataIntegrityViolationException` ⇒ category `INVALID`) | `OrderedKafkaRecovery.quarantine` |
| BLOCKED (rebalance) | `onPartitionsAssigned` finds the oldest non-resolved row with `BLOCKED` ⇒ `consumer.pause` | `OrderedKafkaRecovery.configure` |
| BLOCKED → RETRY_REQUESTED | Script refuses unless the row is `BLOCKED`; dry run by default | [kafka-recovery.py](../../../scripts/kafka-recovery.py) |
| RETRY_REQUESTED (restart) | `onPartitionsAssigned` ⇒ `consumer.seek(originalOffset)` | `OrderedKafkaRecovery.configure` |
| RETRY_REQUESTED → RESOLVED | Record interceptor `success` updates the matching row; metric `vellumhub.kafka.recovery.resolved` | `OrderedKafkaRecovery.configure` |

Metrics: `vellumhub.kafka.recovery.retry`, `.quarantined` (with `category`), `.resolved`. Operator procedure and crash windows: [Kafka recovery runbook](../../KAFKA_RECOVERY_RUNBOOK.md). Tables: Engagement `V9__kafka_consumer_quarantine.sql`, Recommendation `V5__kafka_consumer_quarantine.sql`.
