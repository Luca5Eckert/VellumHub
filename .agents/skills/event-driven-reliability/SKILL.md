---
name: event-driven-reliability
description: Review Kafka event contracts, transactional outbox, projections and consumers for correct failure and recovery behavior.
---

# event-driven-reliability

## Trigger
VellumHub Kafka producers/consumers, schema evolution, transactional outbox, retry/DLT, projections, distributed reads or a new stream.

## Procedure
1. Read actual service ownership, affected ADRs, contract module, issue and architecture-fitness tests. Identify producer, consumer, record key, schema and database transaction.
2. Make the invariant explicit: durable business change + publication intent, delivery semantics, message ordering scope, replay/idempotency and allowed staleness.
3. Analyze failure windows separately: DB rollback, committed outbox but unpublished event, broker unavailable, ACK ambiguity, consumer crash after DB commit, duplicate delivery, poison pill, revision gap and schema incompatibility.
4. Require transactional deduplication/checkpoint or idempotent upsert where the domain demands it. Do not claim exactly-once effects because Kafka has delivery options.
5. Validate schema evolution with frozen compatibility fixtures; track consumer rollout before breaking producer change. Avoid an unjustified registry/format migration.
6. Prefer PostgreSQL + broker-backed Testcontainers for distributed failure proofs. Reuse the project's supported versions and existing suites.
7. Validate static fitness checks and affected modules; run opt-in distributed E2E when relevant and available. Report skipped Docker coverage explicitly.
8. Observe lag, retries, outbox age, DLT volume, freshness and error rate with controlled cardinality; do not invent SLAs.

## Specific project entry
Read docs/ARCHITECTURE_FITNESS_FUNCTIONS.md, docs/architecture/, tools/architecture/ and service-specific tests before editing.

## Output
Event invariant/failure matrix -> evidence for each consumer -> compatibility/recovery gaps.

## Reference
Original project specialization based on the VellumHub architecture and event-driven engineering practices.
