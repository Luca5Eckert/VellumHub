# Catalog Book Events — ECST and Transactional Outbox

Status: proposed in [PR #296](https://github.com/Luca5Eckert/VellumHub/pull/296). This is not evidence of a production rollout.

## Overview

Catalog owns Book. Each committed create/update/soft-delete persists one event in the same PostgreSQL transaction using the shared `OutboxWriter`. The existing polling publisher sends to Kafka after commit and sets PUBLISHED only after the broker ACK.

Do not create another Book-specific dispatcher or duplicate the reading-progress Outbox. `JdbcOutboxStore` fences consecutive revisions within a single (aggregate_type, aggregate_id), while other books and Reading Progress continue independently.

## Event contract

- Topic: `book-events`
- Kafka key: `bookId`
- Spring JsonSerializer type alias: `book_changed_event`
- Payload: `BookChangedEvent(eventId, occurredAt, bookId, revision, kind, state)`
- `kind`: CREATED, UPDATED, DELETED, BASELINE.
- `state`: full public snapshot (title, description, releaseYear, coverUrl, author, isbn, pageCount, publisher, genres).
- DELETED has null state; other kinds require a complete snapshot.
- No passwords, tokens, JPA entities, or internal persistence fields.

Revision comes from `Book.@Version` **after** `saveAndFlush`. New aggregates start at revision 0; a migrated Book starts from a BASELINE at its existing revision. Do not mistake aggregate revision for a schema version.

## Producer transaction

All Book mutation paths register their change via `BookChangeWriter`, which isolates application/domain intent from Kafka. On JDBC insert failure, the Book mutation rolls back; on Kafka failure, the committed outbox event remains pending.

Participating paths: create, import ISBN, approve request, update attributes, update/upload cover, soft delete. No-op attribute updates do not generate a new revision. A null genre list means unchanged; an explicitly empty list clears it.

## Consumer projections

- Engagement: maintains only Book identity in BookSnapshot. DELETE marks BookSnapshot inactive instead of deleting a row referenced by ratings, reactions or reading sessions. UPDATE advances checkpoint without materializing unnecessary attributes.
- Recommendation: updates metadata and vector BookFeature. It skips inference when semantic fields have not changed, but recomputes when the embedding is missing.
- Both: `book_event_offsets` row lock and effect share one transaction, ignoring duplicates and rejecting gaps.
- On poison messages the dedicated Kafka container retries without skipping to later revisions. This protects order but can halt a partition indefinitely.

## Rollout

1. Provision `book-events` and consumers with `book_changed_event` mapping.
2. Drain old created/updated/deleted-book topics while writers are frozen.
3. Migrate Engagement V7 and Recommendation V3. Deploy new consumers before Catalog producers.
4. Apply Catalog Flyway V3 baseline: snapshots of active books in the existing Outbox.
5. Start writers and monitor backlog/lag/checkpoint convergence.
6. Retire legacy listeners only after successful cutover.

Do not rely on cross-topic ordering or modify topic partition count without planning migration.

## HTTP / OpenAPI

HTTP 201, 200 or 204 reflect the local Catalog transaction only. They do not imply that another service has consumed the event. Book's HTTP surface is unchanged and is described in `BookController` OpenAPI annotations; this file documents the Kafka contract separately.

## Observability

- Metrics: `vellumhub.outbox.pending`, `vellumhub.outbox.failed`, `vellumhub.outbox.published`.
- Structured logs: `operation`, `bookId`, `eventId`, `kind`, `revision`, `topic`, `attempt`.
- Use eventId to correlate the Catalog enqueue, broker publish and consumer effect. Do not log raw payloads or cover bytes.

## Constraints and trade-offs

- At-least-once transport, not exactly-once physical delivery.
- More DB writes and storage in exchange for durable publication.
- Cover upload files are outside the PostgreSQL transaction: orphan cleanup is an outstanding operational concern.
- Heavy embedding computation under a consumer transaction can hold its per-book lock; profile and measure before optimizing.
- Purging PUBLISHED rows requires a documented retention policy.
- Consumers of other interaction topics still need their own durability/idempotency work (#200, #202, #272).

See `docs/DISTRIBUTED_TESTING.md` for distributed test infrastructure.
