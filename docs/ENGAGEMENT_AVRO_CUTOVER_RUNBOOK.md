# Engagement Ratings / Reactions JSON → Avro cutover (#350)

Status: implementation and CI validation; **not evidence of production rollout**. Source: [PR #362](https://github.com/Luca5Eckert/VellumHub/pull/362). Phase 1 Reading Progress: [PR #361](https://github.com/Luca5Eckert/VellumHub/pull/361).

## Invariants and wire contracts

| Stream | Legacy JSON | Dedicated Avro topic | Kafka key | Aggregate checkpoint |
| --- | --- | --- | --- | --- |
| Ratings | `engagement.ratings.v1` | `engagement.ratings.v1.avro` | `userId` String | `RATING / ratingId` |
| Reactions | `engagement.reactions.v1` | `engagement.reactions.v1.avro` | `userId` String | `REACTION / reactionId` |

Ratings contains Created, Updated, Deleted and Baseline; Reactions contains Changed and Baseline. Six **TopicRecordNameStrategy** subjects in the Registry manifest isolate these record types on two Avro topics. All subjects use BACKWARD_TRANSITIVE. Avro wire framing uses Confluent-compatible schema IDs from the approved persistent Registry. Do not mix Avro bytes into a JSON topic or duplicate a business fact with a new `eventId`.

Engagement Flyway V10 preserves historical JSONB Outbox rows and adds **per-row immutable** `wire_format` (`JSON` default) and `payload_schema_version` (1 default). These are distinct from aggregate revision and Registry schema version. The dispatcher sends each row to its captured destination independently of the process's current writer flag. Producer failures must leave the row pending; the database transaction and its event ID must not be replaced. Outbox transport validates event/topic/key/format identity.

Recommendation has isolated Avro listener groups `recommendation-service-ratings-avro` and `recommendation-service-reactions-avro`, reusing `processed_engagement_events`, `engagement_event_offsets`, the transactional UserProfile use cases, and ordered quarantine/DLT. A baseline only advances a checkpoint; revision gaps do not advance it. Keep the same aggregate identity and partitioning assumptions across formats. Do not reset an existing checkpoint during format conversion.

## Controls (defaults are opt-out)

| Owner | Environment variable | Default |
| --- | --- | --- |
| Engagement writer | `ENGAGEMENT_RATINGS_WIRE_FORMAT` | `json` |
| Engagement writer | `ENGAGEMENT_REACTIONS_WIRE_FORMAT` | `json` |
| Engagement producer / Recommendation consumer | `ENGAGEMENT_AVRO_REGISTRY_URL` | local ccompat URL |
| Recommendation | `ENGAGEMENT_RATINGS_AVRO_CONSUMER_ENABLED` | `false` |
| Recommendation | `ENGAGEMENT_REACTIONS_AVRO_CONSUMER_ENABLED` | `false` |
| Recommendation | `KAFKA_LEGACY_CONSUMERS_ENABLED` | `true` |

**Important:** `KAFKA_LEGACY_CONSUMERS_ENABLED` is shared with other legacy listeners; do not flip it for a single stream without confirming Book and Reading Progress have already completed their own cutover. If legacy and Avro consumers both subscribe to facts representing the same business transaction, confirm a single effect in the durable checkpoint ledger before proceeding.

## Preflight: go / no-go

1. Only use approved disposable or staged infrastructure. Confirm Registry persistence, six approved subjects and IDs, independent Kafka ACLs, consumer DLT permissions, stable partition count, matching service versions and database V10. Do not infer production readiness from CI.
2. Capture timestamped evidence: stream JSON topic end offsets, consumer committed offsets, `engagement_event_offsets` per aggregate, `processed_engagement_events` counts, any quarantine/replay blocks, and Outbox `status` counts grouped by `aggregate_type,topic,wire_format`. Record source-of-truth Rating/Reaction revision and UserProfile baseline; never inspect raw personal payload in logs.
3. Register all six schemas with approved Registry workflow, with auto registration disabled in applications. Test a cold-cache schema ID resolution failure (fail closed, no unexpected JSON fallback).
4. Enable **only** the intended Avro Recommendation listener(s) first, with their distinct consumer groups and an empty or reconciled Avro backlog. Verify that their projection logic uses the existing checkpoints, not a second read model.

## Controlled cutover, independently for RATING and REACTION

5. Freeze exactly one aggregate type using a reviewed SQL transaction (the writer holds `FOR SHARE` through its business transaction, so a freeze update waits for in-flight commits):

   ```sql
   BEGIN;
   UPDATE engagement_write_gate
   SET frozen = true, updated_at = now()
   WHERE aggregate_type = 'RATING' AND frozen = false;
   -- Require exactly one row affected; otherwise stop.
   COMMIT;
   ```

   For Reactions use `aggregate_type = 'REACTION'`. Do **not** stop the Outbox relay during draining.

6. Drain legacy JSON Outbox rows of this aggregate type and the JSON Kafka topic consumer group to recorded high-watermarks; ensure no stuck quarantine, missing revision, duplicate logical fact or unenforced baseline. Compare source aggregates and UserProfile effects for each affected aggregate.
7. Change **only that stream's** writer flag to `avro`; roll all Engagement replicas and verify identical configuration on all instances **while frozen**. Unfreeze that type with a reviewed SQL transaction setting `frozen=false`.
8. Publish representative Created/Updated/Deleted/Baseline (Ratings) or Changed/Baseline (Reactions) and assert unchanged key, `eventId`, occurredAt, revision, state delta, transactionally committed checkpoint and duplicate no-op. Watch Outbox pending/failed, source topic/partition/offset, Registry errors, consumer lag and ordered DLT; do not log full Avro/JSON payloads.
9. Only retire legacy listeners after *all streams affected by the shared legacy switch* have been separately drained and reconciled. Do not interpret enabling Avro consumers as proof of cutover.

## Rollback or blocked cutover

Freeze the specific aggregate type again and capture an Avro topic high-watermark. Drain **all Avro-stamped Outbox rows** and let Recommendation's Avro group process through that watermark with no revision gaps or outstanding quarantine. If Registry/Kafka is unavailable or a poison message blocks processing, **keep that stream frozen**; do not simply flip the writer flag, reset offsets or create new facts. Once reconciled, restore `json` routing across all Engagement replicas, unfreeze the type and keep historical schema IDs, pending rows and evidence. Previously created Avro-stamped Outbox rows remain Avro for safe retry, even after rollback. Do not drop V10 or rewrite their `eventId`.

## Required acceptance evidence

- Kafka, PostgreSQL and Registry: six distinct subjects, historical v1 and compatible/incompatible v2 fixtures, actual mapper serialization/deserialization.
- Both streams: baseline checkpoint-only, CREATED/UPDATED/DELETED and reaction transitions, duplicate redelivery, revision gap and revision collision, original raw-wire DLT with source position, identity-preserving replay.
- Legacy JSON pending Outbox after upgrade; freeze/drain across in-flight writers; fail-closed Registry/Kafka; actual coordinated rollback on representative data.
- Retain GitHub Actions job URLs, code SHA and sanitized SQL reconciliation results **per stream**. Passing mapper, topology or partial distributed tests does not establish a complete rollback rehearsal.
