# Kafka consumer idempotency (#200)

Status: implemented in code; production cutover and CI/distributed-test results must be verified independently. This document describes Recommendation's versioned streams, not a guarantee of exactly-once Kafka delivery.

## Problem and guarantee

Kafka consumption is at least once. A broker ACK, rebalance or restart may cause a previously committed event to be delivered again. Rating, reaction and reading-progress deltas must not modify `user_profiles` twice; Book metadata and vector embeddings must not be recomputed for an already-applied revision.

Recommendation guarantees **one committed effect per accepted aggregate revision**, provided consumers use the canonical versioned streams and the local PostgreSQL transaction is intact:

1. lock the checkpoint row in PostgreSQL;
2. skip revisions already applied, or reject a missing predecessor;
3. apply the local projection/profile delta;
4. persist the immutable `eventId` and advance the checkpoint in the **same transaction**;
5. acknowledge consumption only after the transaction commits.

If step 3 or 4 fails, the entire transaction rolls back. Kafka may redeliver, but the committed checkpoint makes it a no-op. This is an effect-once invariant, not an exactly-once transport claim. External side effects must not be introduced inside this transaction without their own reliability contract.

## Canonical streams and database state

| Kafka stream | Partition key | Consumer | Identity / ordering |
|---|---|---|---|
| `book-events` | bookId | `ApplyBookEventUseCase` | `book_event_offsets` + `processed_events` |
| `reading-progress-events` | bookProgressId | `ProcessOrderedReadingProgressUseCase` | `reading_progress_offsets` + `processed_events` |
| `engagement.ratings.v1` | userId | `EngagementEventProcessor` | `engagement_event_offsets` + `processed_engagement_events` |
| `engagement.reactions.v1` | userId | `EngagementEventProcessor` | `engagement_event_offsets` + `processed_engagement_events` |

The Book/Reading Progress event identity registry already exists in Recommendation Flyway V2; Book uses that same registry, with no new table. Engagement has its own registry from V4. Primary keys on `event_id` are the concurrency-safe uniqueness boundary. An ID reused for an unapplied Engagement aggregate/revision is an error, not an acknowledgement. A legitimate duplicate with `revision <= last_revision` is returned as `DUPLICATE` before applying another effect.

Checkpoints are **per aggregate**, not Kafka offsets. A `BASELINE` is an initial migration checkpoint: Reading Progress and Engagement baselines do not replay old user signals. Book baseline materializes the current public snapshot. A missing predecessor revision is a retryable processing failure; do not commit a later revision as a substitute.

UserProfile's optimistic `@Version` prevents lost writes when different aggregates/streams modify the same user concurrently. On an optimistic conflict the checkpoint must roll back with the profile change; the Kafka handler must retry. The Book consumer holds a row lock while generating semantic embeddings; measure the critical-section latency before optimizing.

## Stable event identity

New versioned contracts require a nonnull immutable `eventId`. Producers must preserve the same identity across outbox attempts and broker re-deliveries. Never fabricate a random event ID inside a consumer to make a legacy payload appear safe. `revision` is the source aggregate state revision, not the schema version.

The identity registries need an explicit retention policy before compaction or deletion. The revision checkpoint still protects against repeat delivery of older revisions after identity-record cleanup, but removed identity records no longer detect UUID reuse across different aggregates.

## Legacy topics and controlled cutover

Legacy listeners in Recommendation still include `created/updated/deleted-book`, `created/updated-reading-progress`, `created/updated-rating` and `user-reaction-changed`. Some legacy listeners apply deltas without durable revision/identity checkpoints. They are **not** covered by this guarantee.

A compatibility setting controls those listeners:

```properties
app.kafka.legacy-consumers.enabled=${KAFKA_LEGACY_CONSUMERS_ENABLED:true}
```

The default remains `true` to avoid silently dropping messages in deployments that have not migrated. Do not leave legacy and canonical producers/listeners processing the **same logical mutation** at the same time. Those event IDs and representations do not provide a universal cross-topic deduplication contract.

Recommended deployment sequence (repeat for each bounded-context stream):

1. Inventory active producers, consumer groups, lag and destination read-model state. Prepare a rollback procedure.
2. Provision canonical topics and type aliases, and apply Recommendation V2/V3/V4 migrations. Deploy canonical consumers before enabling canonical producers.
3. Freeze affected legacy writers, drain relevant legacy topics and retries, then record consumer lag and consistency checkpoints. Quarantine/reconcile any outstanding legacy DLT records separately.
4. Apply Catalog/Engagement baseline migrations and enable the versioned Outbox producers as documented in `BOOK_EVENTS.md` and `ENGAGEMENT_EVENTS.md`.
5. Disable Recommendation legacy consumers by setting `KAFKA_LEGACY_CONSUMERS_ENABLED=false` and restart/deploy as appropriate. Coordinate with Engagement legacy listeners separately.
6. Resume writers. Confirm baselines applied, newer revisions advance and the read model converges. Monitor consumer lag, exceptions, outbox backlog and duplicate counters. Only then retire old topic contracts.

A runtime property change alone does not drain old messages or reconcile already duplicated profile weights. If overlap occurred, reconstruct affected read models from authoritative facts rather than applying an arbitrary compensating event. **Do not mark production cutover complete solely because code was merged.**

## Kafka listener payload binding

The canonical Engagement listener handles several fact contracts on two topics. With Spring Kafka 4 it deliberately declares `ConsumerRecord<String, Object>` and passes `record.value()` to the domain processor. An unannotated `Object` parameter can be resolved as the entire Kafka record instead of the fact payload, causing an `IllegalArgumentException` and repeatedly seeking the same offset under the ordered retry policy. The real Kafka duplicate-delivery test exercises this boundary.

## Observability

`vellumhub.kafka.events.consumed` counts successfully handled Kafka records, including duplicate deliveries. `vellumhub.kafka.events.duplicate` counts records recognized as already applied in canonical Recommendation streams. `vellumhub.kafka.event.processing.duration` uses low-cardinality `result=success|duplicate|failure`. The existing failed-consume metric still counts exceptions, including revision gaps or identity collisions.

Structured logs include an `operation` marker and `eventId`, aggregate and revision when available. Use IDs only in logs/traces; **never** as Prometheus labels. Do not log JWTs, secrets or complete user payloads.

## Verification

`ConsumerIdempotencyDistributedTest` uses actual Kafka and PostgreSQL/pgvector, with a deterministic embedding provider. It covers:

- Book replay and concurrent delivery without another embedding computation;
- Book projection exception and rollback of both event ID and revision checkpoint;
- Reading Progress replay without duplicate profile deltas and failure on missing features;
- Engagement UUID collision across distinct aggregates;
- Rating redelivery through Kafka, with a duplicate counter and stable persisted user score.

Unit tests also cover baseline, already-applied revision, missing predecessor and conflict handling.

```bash
mvn -DexcludedGroups=distributed clean verify
mvn -Pdistributed-e2e -pl integration-tests/distributed-e2e -am -Dgroups=distributed test
```

Do not describe these scenarios as passing until CI or local execution confirms success. The distributed test framework requires Docker.

## Boundaries and next work

- Strict blocking retries preserve sequence but a poison message may stall a partition. Bounded retry, DLT, replay and operational recovery belong to [#272](https://github.com/Luca5Eckert/VellumHub/issues/272).
- End-to-end observability evidence belongs to [#214](https://github.com/Luca5Eckert/VellumHub/issues/214).
- The current implementation does not use Redis as an idempotency store, Kafka transactions, Debezium, or a generic cross-service deduplication framework. PostgreSQL is sufficient because projection writes and checkpoints must commit atomically.
- The score/vector compensation for a deleted historical rating does not perfectly reconstruct all prior normalization steps. Idempotency prevents a delta from applying twice, but does not make the ranking algorithm mathematically invertible.
