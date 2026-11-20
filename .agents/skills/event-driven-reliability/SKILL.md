---
name: event-driven-reliability
description: Design or review VellumHub Kafka flows — transactional outbox producers, ordered consumers with per-aggregate revision checkpoints, duplicate and gap handling, retry/DLT quarantine and replay, read-model projections, JSON and Avro contracts with Schema Registry, and JSON→Avro cutovers. Use it whenever code under outbox, kafka, consumer, book_event, EngagementEventProcessor or lib/kafka-*contracts changes, a new stream or event field is added, a consumer misbehaves, or someone asks about delivery guarantees, ordering or replays.
---

# Event-driven reliability (VellumHub)

Kafka here is at-least-once. Correctness comes from the database: outbox rows written in the business transaction, and consumers that commit projection, processed-event record and checkpoint together. Reason about each failure window explicitly instead of trusting a delivery option.

## Procedure
1. Identify producer, topic, key, schema, consumer group, projection and the transaction on each side. Read the diagrams first: `docs/architecture/flows/book-outbox.md`, `states/outbox-delivery.md`, `flows/ordered-consumer.md`, `states/kafka-quarantine.md`, `states/event-wire-format.md`.
2. State the invariant: what must commit atomically, ordering scope (per aggregate), replay and idempotency key, allowed staleness.
3. Walk the failure windows: DB rollback; committed outbox not yet published; broker down; lost ACK (duplicate publish); consumer crash after DB commit; duplicate delivery; poison record; revision gap; incompatible schema.
4. Ordered consumers lock the checkpoint, treat `revision <= last` as duplicate (same event ID) or collision (different ID), and require `revision == last + 1`; a BASELINE may only seed an empty checkpoint. Keep that rule in a named method and keep its messages stable, because operators search logs for them.
5. Exhausted records go to `OrderedKafkaRecovery`: diagnostic DLT copy, `kafka_consumer_quarantine` = BLOCKED, partition paused, original offset uncommitted. Engagement and Recommendation hold identical copies guarded by `tools/architecture/test_ordered_recovery_parity.py`; change both or neither. Never re-append DLT records to an ordered topic.
6. Schemas: Avro in `lib/kafka-avro-contracts` under BACKWARD_TRANSITIVE Registry subjects, JSON records and wire fixtures in `lib/kafka-contracts`. Producers resolve schemas and never auto-register. JSON is still the default wire format; switching a stream follows the per-stream cutover runbooks (`docs/*_AVRO_CUTOVER_RUNBOOK.md`). Every outbox row stamps an immutable `wire_format`.
7. Prove distributed behaviour with PostgreSQL + Kafka Testcontainers, and report skipped Docker coverage explicitly.
8. Observe lag, retries, outbox age, quarantine and DLT volume with bounded label cardinality; do not invent SLAs.

## Output
Invariant and failure-window matrix, evidence per consumer, compatibility and recovery gaps.

Original project specialization based on the VellumHub architecture.
