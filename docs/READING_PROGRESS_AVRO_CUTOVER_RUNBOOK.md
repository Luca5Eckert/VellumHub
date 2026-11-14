# Reading Progress JSON → Avro cutover (#350)

Status: implementation in progress; **not evidence of a production migration**.

## Boundaries

- Legacy topic: `reading-progress-events`. Avro-only topic: `reading-progress-events.avro`.
- Kafka key: `bookProgressId.toString()`. Keep the partition count stable and record actual per-key partition assignments before switching.
- Writer: `com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent`.
- Registry: Apicurio ccompat v7, TopicRecordNameStrategy, BACKWARD_TRANSITIVE, `auto.register.schemas=false`; register the Git-approved contract through the controlled #348 workflow before enabling writers.
- Catalog JSONB Outbox retains per-row `wire_format` and `payload_schema_version`; neither changes after enqueue. V5 adds a second explicit Avro route, not another Outbox.
- Engagement ReadingSessionEntry and Recommendation UserProfile use their **existing** transactional Reading Progress checkpoint use cases. Never create a second projection or reset checkpoints.
- DLT is diagnostic and preserves raw Avro; replay must seek the **original Kafka offset** after clearing its partition block.

## Environment controls

| Service | Variable | Default |
| --- | --- | --- |
| Catalog | `READING_PROGRESS_EVENTS_WIRE_FORMAT` | `json` |
| Catalog | `READING_PROGRESS_AVRO_REGISTRY_URL` | local ccompat endpoint |
| Engagement, Recommendation | `READING_PROGRESS_AVRO_CONSUMER_ENABLED` | `false` |
| Engagement, Recommendation | `READING_PROGRESS_AVRO_REGISTRY_URL` | local ccompat endpoint |

Enable the Avro consumers first. Do not enable the writer during preparation. Registry URLs, Kafka ACLs and broker security are independent.

## GO/NO-GO and cutover (lab / approved environment only)

1. Validate Registry's approved subject and ID, Kafka ACLs, partitions, DLT permissions and a cold-cache consumer with the historical writer bytes.
2. Capture *per-partition* end offsets and consumer-group committed positions for JSON. Capture both consumer databases' `reading_progress_offsets`, outstanding ordered quarantine and Catalog Outbox `BOOK_PROGRESS` counts by `topic,wire_format,status`. Examine whether a prior migration left unreconciled baselines.
3. Enable the two Avro listeners with their own groups. Verify no unexpected historical Avro backlog before beginning.
4. Freeze **only Reading Progress** writers; an operator uses an approved transaction:
   ```sql
   BEGIN;
   UPDATE reading_progress_write_gate SET frozen=true, updated_at=now()
     WHERE id=1 AND frozen=false;
   -- require exactly one row updated; else stop
   COMMIT;
   ```
   The update waits for in-flight writers holding `FOR SHARE`. The Catalog relay remains active.
5. Drain old `BOOK_PROGRESS` JSON Outbox rows and JSON topic offsets in **both** Engagement and Recommendation. Require zero unresolved blocks or revision gaps, and compare materialized projections with their source aggregates.
6. Change only Catalog's `READING_PROGRESS_EVENTS_WIRE_FORMAT=avro`, roll all replicas, verify every instance has the new setting, then unfreeze by setting the gate to `false` in an operator-approved transaction.
7. Produce CREATED and UPDATED test facts and verify the same `eventId`, `bookProgressId`, key and revision appear in the Avro frame, Engagement history and Recommendation UserProfile exactly once. BASELINE is checkpoint-only. Inspect registry lookup failures, outbox age, Kafka lag and DLT without logging complete payloads.

## Rollback

Freeze Reading Progress again, then drain **all Avro-stamped** Outbox rows and Avro Kafka offsets up to a recorded high-watermark in both consumers. No JSON listener may see Avro bytes and no unprocessed revision may be skipped. If a poison event or Registry outage blocks the drain, **stay frozen**. Otherwise restore Catalog JSON routing, roll all replicas and unfreeze. Existing Avro-stamped rows remain Avro even after the flag flips. Keep the Registry schema IDs, DLT and audit evidence; do not drop V5 metadata.

### Evidence required

Real PostgreSQL/Kafka/Apicurio tests must show old JSONB rows sent after upgrade, cold Registry failure with no invalid publication, ACK ambiguity, duplicate delivery, gap handling, raw-wire DLT and recovery, both actual consumers, and complete freeze/drain/cutover/rollback. Passing configuration rendering or unit mapper tests is **not** evidence of this operational rehearsal.
