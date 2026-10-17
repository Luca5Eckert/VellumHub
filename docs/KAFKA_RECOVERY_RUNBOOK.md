# Kafka consumer recovery: retry, quarantine, DLT and replay (#272)

The source of truth for Book, Reading Progress and Engagement events remains the
original Kafka topic and its producer Outbox. A DLT is an immutable diagnostic
copy, **not** the continuation of an ordered stream.

## Scope and contract

- Canonical ordered consumers: Recommendation Book, Reading Progress,
  Rating/Reaction; Engagement Book and Reading Progress.
- Default: initial processing plus three blocking retries at 1-second fixed
  intervals (`app.kafka.recovery.backoff-ms`,
  `app.kafka.recovery.max-retries`). `IllegalArgumentException`,
  `DataIntegrityViolationException` and deserialization exceptions are
  not retried.
- On exhaustion: produce a DLT record and await Kafka broker acknowledgement;
  upsert the source-coordinate quarantine row; pause the affected partition;
  do not commit the failed source offset.
- On rebalance and process restart: load the quarantine marker. `BLOCKED`
  partitions remain paused; `RETRY_REQUESTED` partitions seek the **original**
  source offset. A successful listener execution transitions the record to
  `RESOLVED`. The business projection/checkpoint transaction remains
  authoritative, and re-delivery remains idempotent.
- Existing *legacy* retry topics preserve their three-attempt/DLT behavior.
  Their consumers do **not** all have stable event identity or checkpoint
  guarantees. Do not treat legacy DLT replay as universally safe.

The two consumer databases each contain `kafka_consumer_quarantine` and
`kafka_recovery_audit` (Recommendation Flyway V5, Engagement Flyway V9).
Isolation is by consumer group + original topic + partition + offset. Each
service has its own table, so two groups can recover a source record
independently.

### Expected DLT shape

A failed `book-events` record in Recommendation is copied to
`book-events.recommendation-service-dlt`; in Engagement to
`book-events.engagement-service-dlt`. The topic is separate per destination
consumer. Other ordered topics follow the same `<source>.<service>-dlt` naming.

Payload: serialized original domain event; key: original Kafka key.
Allowlisted headers:

- `__TypeId__` (original type alias) and optional `x-correlation-id`
- `x-vellum-source-topic`, `x-vellum-source-partition`,
  `x-vellum-source-offset`, `x-vellum-consumer-group`
- `x-vellum-failure-category`, `x-vellum-event-id` (when valid)

Exception stack traces, arbitrary original headers, error messages and
authentication details are intentionally omitted. The DLT is still a copy
of potentially sensitive business data: restrict topic ACLs, retention and
operator access. Never paste its payload into tickets.

**Delivery trade-off:** DB quarantine writes and DLT publication do not
participate in a distributed transaction. Publish is acknowledged first.
A crash between the broker acknowledgement and DB insert can generate
multiple DLT copies on redelivery. Deduplicate operational reports by
(source topic, partition, offset, destination group). This does not
duplicate business effects.

## Investigation and recovery

1. Confirm a `vellumhub_kafka_recovery_quarantined_total` increase and
   growing lag, or inspect `operation=kafka_quarantined` in Loki.
   Determine which consumer group is blocked.
2. Check the DLT and source-offset coordinates; never assume a DLT offset
   equals the source offset. Correlate by the `x-vellum-source-*` headers.
3. Fix the underlying cause: dependency recovery, missing BookFeature
   baseline, schema mapping, migration or invalid event. A permanent
   contract violation requires controlled repair, not a blind replay.
4. Inspect using a read-only command (no Kafka producer required):

   ```bash
   pip install -r scripts/kafka-recovery-requirements.txt
   export VELLUM_RECOVERY_DSN='postgresql://<operator>@<db-host>/<consumer-db>'
   python scripts/kafka-recovery.py inspect \
     --group recommendation-service --topic book-events --partition 0 --offset 123
   ```

5. Request recovery after review; the CLI defaults to dry run, and
   requires explicit `--execute`, operator and reason:

   ```bash
   python scripts/kafka-recovery.py resume \
     --group recommendation-service --topic book-events --partition 0 --offset 123 \
     --operator engineer@example.com --reason 'Book projection dependency repaired'
   python scripts/kafka-recovery.py resume \
     --group recommendation-service --topic book-events --partition 0 --offset 123 \
     --operator engineer@example.com --reason 'Book projection dependency repaired' --execute
   ```

6. Restart **only the affected consumer service**. The rebalance callback
   seeks the retained original offset. Verify `RESOLVED`, checkpoint
   advancement, no new DLT publication and normal lag convergence.
   If failure persists, the record returns to `BLOCKED`; keep investigating.

### Independent DLT replay

The CLI exposes `replay-dlt` for future **identity-bearing independent**
consumer contracts, not for the four canonical ordered streams. It
refuses records without validated provenance headers and a stable
`eventId`, refuses ordered topics, and retains the original payload,
key, type alias and event identity. Example:

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
python scripts/kafka-recovery.py replay-dlt \
  --dlt-topic independent-topic.recommendation-service-dlt \
  --partition 0 --offset 12 \
  --operator engineer@example.com --reason 'Fixed downstream dependency' --execute
```

Replay submission and delivery outcome are audited separately, because
PostgreSQL and the broker cannot be committed atomically. An acknowledged
send is **not** proof that a downstream projection applied it. Verify the
checkpoint or query result before marking an incident resolved.

Legacy topics without an event ID and the new provenance headers remain
manual reconciliation tasks; the CLI rejects their automatic replay. Do
not invent event IDs inside recovery tooling.

## Monitoring and review

Metrics use bounded `service`, `topic`, `consumer_group` and, for
quarantine, `category` tags. Never use `eventId`, partition or offset
as Prometheus labels.

- `vellumhub_kafka_recovery_retry_total`: retry callbacks.
- `vellumhub_kafka_recovery_quarantined_total`: records exhausted.
- `vellumhub_kafka_recovery_resolved_total`: recovered source records.

`kafka_consumer_quarantine` is the durable source of current blocked
partitions, even if the application restarts or a metric counter resets.
Alert on any new quarantine and on persisting blocks. Review DLT
retention, topic ACLs and records older than the incident SLA.

## Verification and rollback

Run non-distributed Maven tests and the existing Docker-backed
distributed suite. Verify one canonical ordered failure against
PostgreSQL quarantine, Kafka DLT headers and **absence** of a processed
checkpoint. Test duplicate delivery and retry after repair, plus the
legacy retry/DLT regression suite. Any failure must keep the PR draft.

On rollback, **do not discard quarantine rows** or manually advance
consumer offsets. Revert the deployment only after reconciling those
records with their original source offsets. Never delete DLT or
quarantine state as a means of unblocking a service.

Related: [Consumer Idempotency](CONSUMER_IDEMPOTENCY.md),
[Distributed Testing](DISTRIBUTED_TESTING.md),
[Observability Runbooks](OBSERVABILITY_RUNBOOKS.md).
