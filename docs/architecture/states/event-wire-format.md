---
id: vh-event-wire-format
slug: event-wire-format
type: state-machine
system: vellumhub
status: implemented-in-repository
related: [vh-book-outbox, vh-outbox-delivery, vh-ordered-consumer]
---

# State machine — JSON → Avro wire format per stream

Each canonical stream (Book, ReadingProgress, Ratings, Reactions) is switched independently. The **default today is JSON** (`*-wire-format=json`, Avro consumers disabled). Every Outbox row stamps an immutable `wire_format` and `payload_schema_version`, so a row keeps its destination even if the producer flag later changes.

```mermaid
stateDiagram-v2
    [*] --> JSON: default configuration
    JSON --> DUAL_READ: enable Avro consumers (own groups, same projections and checkpoints)
    DUAL_READ --> JSON: disable Avro consumers (nothing was published as Avro)
    DUAL_READ --> FROZEN: freeze aggregate writes, drain JSON Outbox and consumers
    FROZEN --> AVRO: producer flag = avro, replicas rolled, unfreeze
    AVRO --> ROLLBACK_FROZEN: freeze again, drain every AVRO-stamped row
    ROLLBACK_FROZEN --> DUAL_READ: producer flag = json, unfreeze
    note right of FROZEN
      Poison event, Registry outage or
      revision gap: stay frozen until repaired.
      Never re-encode bytes or skip a revision.
    end note
```

| State | Producer (`app.*.events.wire-format`) | Consumers |
| --- | --- | --- |
| JSON | `json` | JSON listeners only |
| DUAL_READ | `json` | JSON + Avro listeners (`app.*.avro.consumer-enabled=true`) |
| FROZEN / ROLLBACK_FROZEN | unchanged; DB write gate closed | both, until backlog and checkpoints match |
| AVRO | `avro`; schema resolved from Registry, never auto-registered | both kept until formal retirement |

Schemas live in [`lib/kafka-avro-contracts`](../../../lib/kafka-avro-contracts) under `BACKWARD_TRANSITIVE` subjects ([Registry operations](../../SCHEMA_REGISTRY_OPERATIONS.md)). Step-by-step commands and GO/NO-GO checks per stream: [Book](../../BOOK_AVRO_CUTOVER_RUNBOOK.md) · [Engagement](../../ENGAGEMENT_AVRO_CUTOVER_RUNBOOK.md) · [Reading progress](../../READING_PROGRESS_AVRO_CUTOVER_RUNBOOK.md). A fresh environment can start directly in Avro: [fresh Avro installation](../../FRESH_AVRO_INSTALLATION.md).
