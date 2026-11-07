# Book JSON→Avro cutover and rollback — issue #349

Status: **implementation PR, NOT production rollout**. This runbook describes operator-controlled actions; the existence of code, flags or passing static Kustomize gates is **not** proof of a rehearsed cutover. Never perform these steps on shared/production environments without change approval, backups and validated Registry OIDC/TLS.

## Boundary and invariants

Legacy Book is \`book-events\` with Spring JSON; new Book is \`book-events.avro\` with Confluent-compatible Avro schema IDs via Apicurio 3.1.7. Kafka key stays the string \`bookId\`. Writer schema is in \`lib/kafka-avro-contracts/src/main/avro/BookChangedEvent.avsc\`, and Registry subject is \`book-events.avro-com.vellumhub.kafka.avro.book.BookChangedEvent\`. \`auto.register.schemas=false\`, so an operator must register v1/v2 through the #348 controlled publication flow first. The Kafka ACL allowlist is \`infra/kafka/acl-policy.json\` and is distinct from Registry read role authorization.

Catalog \`outbox_events.payload\` remains JSONB. The new Flyway V4 metadata \`wire_format\` and \`payload_schema_version\` are immutable *per row*; old rows default to JSON/1 without rewriting payloads. \`aggregate_version\` remains the Book revision, not the Avro schema version. **Never convert a historical JSON Outbox row to Avro by changing only the service flag.** The relay routes by the stored metadata even after rollback. No dual-publish. Reading Progress and ratings/reactions remain JSON.

The DB write fence \`book_write_gate\` coordinates all Catalog replicas. Book writer transactions take \`SELECT ... FOR SHARE\` in the same transaction that creates the mutation+Outbox. Flipping the row to \`frozen=true\` must wait for in-flight transactions to commit; thereafter new Book mutations fail with HTTP 503 and roll back. The relay is independent and continues draining pending Outbox. A client must retry frozen writes rather than assume they committed. Do not freeze the entire Catalog database or disable the relay.

Only the new Avro listener reads raw bytes; it decodes the wire itself, so the existing ordered \`OrderedKafkaRecovery\` writes original Avro magic-byte+schema-ID bytes to the DLT. DLT is diagnostic, not a continuation of the ordered stream. Resume by the **original** source offset, not by publishing DLT at the tail. The two consumer databases keep independent \`book_event_offsets\`, and Recommendation also holds immutable \`processed_events.event_id\`.

## Preconditions / GO-NO-GO

1. Confirm all images are pinned to approved SHAs and the #347 schema compatibility and #348 Registry persistence CI succeeded **on those SHAs**, without changing historic v1/v2 definitions. Apply **canonical** Kind app overlay and separate Registry local overlay; never deploy a second duplicate full app overlay. In stable clusters, provision SQL-backed Registry with OIDC roles and TLS/CA/secrets first.
2. Explicitly create \`book-events.avro\` with the intended partition count and durable retention and provision read/write/DLT ACLs for Catalog, Recommendation and Engagement. Record partition counts of **both** topics and the actual partition mapping for all active Book IDs: a consistent key serializer alone does not guarantee equivalent partitions if counts differ. Do not change the partition count after cutover.
3. Register Book writer schemas v1/v2 under the approved subject through the protected Registry workflow; verify IDs and that the three applications can resolve them. No application principal may create or modify schemas.
4. Before cutover, capture high-watermarks/end offsets of \`book-events\`, group positions for \`recommendation-service\` and \`engagement-service\`, active quarantines and DLT records, plus Catalog Outbox rows, \`book_event_offsets\` from **both** DBs and both read models. Check new Avro topic is empty or has a documented starting offset. Any unexpected rows/partition blocks/revision gaps are **NO-GO**.

Read-only SQL examples against Catalog (use a read-only principal):

\`\`\`sql
SELECT frozen FROM book_write_gate WHERE id=1;
SELECT topic, wire_format, status, count(*)
  FROM outbox_events WHERE aggregate_type='BOOK'
 GROUP BY topic, wire_format, status ORDER BY 1,2,3;
SELECT event_id, aggregate_id, aggregate_version, topic, wire_format, status,
       attempts, last_error
  FROM outbox_events WHERE aggregate_type='BOOK' AND status <> 'PUBLISHED'
 ORDER BY created_at, aggregate_id, aggregate_version;
\`\`\`

Read-only SQL in *each* consumer database:

\`\`\`sql
SELECT book_id,last_revision FROM book_event_offsets ORDER BY book_id;
SELECT consumer_group,source_topic,source_partition,source_offset,status
  FROM kafka_consumer_quarantine WHERE status <> 'RESOLVED'
 ORDER BY consumer_group,source_topic,source_partition,source_offset;
\`\`\`

The SQL is not itself a reconciliation: compare complete per-Book revisions and materialized data to Catalog, including deleted/inactive books. Use Kafka's consumer-group CLI to capture **per-partition** group lag and end offsets, not only a total count. Preserve logs and snapshots without raw message payloads, PII or Registry credentials.

## Preparation (Catalog still publishes JSON)

1. Render the separate existing Kustomizations and create the Registry Secret and topic/ACLs before starting applications. Kustomize overlays are in \`deploy/kubernetes/overlays/local\` (applications) and \`deploy/kubernetes/schema-registry/overlays/local\` (Registry). They must be deployed in the expected order and with valid local DB/secrets. Staged values are **operator-reviewed commands**, not a GitOps controller:

   \`\`\`sh
   python3 tools/book_avro_cutover/k8s_stage.py prepare
   \`\`\`

2. Apply prepared environment values with approved deployment tooling and wait for all Catalog/Recommendation/Engagement rollouts. Do not enable Book Avro consumers yet; verify Registry at \`http://schema-registry:8080/apis/ccompat/v7\` **inside the Kind cluster**.
3. With Catalog still writing JSON, enable the new consumers (they have separate Avro Kafka groups but call the *existing* projections/checkpoints):

   \`\`\`sh
   python3 tools/book_avro_cutover/k8s_stage.py consumers
   \`\`\`

   Do not start processing historic Avro data in a populated read model without offset/book revision reconciliation. If the Avro topic is nonempty, establish the explicit start offsets and test that no old facts are applied twice. **Never reset \`book_event_offsets\` to zero.**

## Cutover — freeze → drain → switch → unfreeze

A privileged database operator issues the following **after approval**; these are mutating operations, not dry-runs. The commit of the freeze waits for active Book transactions that hold the shared fence lock:

\`\`\`sql
BEGIN;
UPDATE book_write_gate SET frozen=TRUE, updated_at=now()
 WHERE id=1 AND frozen=FALSE;
-- Expect exactly 1 row changed. Otherwise STOP and investigate.
COMMIT;
\`\`\`

Wait until Catalog Book Outbox has **zero** \`PENDING\` and \`IN_FLIGHT\` rows and all previously captured JSON Kafka offsets have been processed by both Book consumer groups. Check no unresolved quarantine and verify per-Book checkpoints/projections. Avoid drainer shutdown or adjusting offsets manually. If the relay cannot drain, **stay frozen** and repair rather than switching to Avro.

Only then run the reviewed producer stage command and wait for all Catalog replicas to roll out while the DB fence remains frozen:

\`\`\`sh
python3 tools/book_avro_cutover/k8s_stage.py producer
\`\`\`

Verify all Catalog pods have \`BOOK_EVENTS_WIRE_FORMAT=avro\`, consumers are enabled, and Registry is healthy with published schema IDs. Unfreeze with a separate approved SQL operation:

\`\`\`sql
BEGIN;
UPDATE book_write_gate SET frozen=FALSE, updated_at=now() WHERE id=1 AND frozen=TRUE;
-- Expect exactly 1 row changed.
COMMIT;
\`\`\`

Run a **new** Book mutation with a recorded original \`eventId\`, and verify the Outbox row is stamped \`book-events.avro\`/\`AVRO\` before observing an ACK; verify actual Kafka wire is magic byte 0 + registered schema ID; verify both Recommendation and Engagement Book read models and checkpoints advanced one revision. Test UPDATED, DELETED and BASELINE semantics separately. Monitor (without event IDs as metric labels): Outbox pending/failed/published, Registry lookup failures, Kafka lag per group, DLT/quarantine and revision gaps. The JSON readers can remain online to process late redeliveries on the old topic, but cannot be allowed to apply a second differently identified copy of the same fact.

## Rollback — never flip the flag while Avro is pending

1. Freeze the Book DB gate again, waiting for in-flight Book mutations. Record Kafka Avro high-watermarks, Catalog Outbox wire format counts and both consumers' Avro group positions/checkpoints. Keep the Avro consumers running until **all** Avro Outbox rows and Kafka offsets up to the recorded cutover point are applied. A row already stamped \`AVRO\` **remains** Avro after the producer flag is rolled back.
2. If there is an unresolvable poison Avro event, Registry outage or revision gap, **remain frozen** until operator repair. Do not send its bytes to JSON, reset offsets, skip a revision or invent a new \`eventId\`.
3. Once Avro is drained and both read models reconciled, run the producer rollback command, wait for all Catalog replicas to have JSON routing, then unfreeze with the SQL above.

   \`\`\`sh
   python3 tools/book_avro_cutover/k8s_stage.py rollback
   \`\`\`

4. Verify the next new Book mutation is JSON on \`book-events\`, that only one read-model effect occurs, and that no Avro rows remain unpublished. Retain Registry schemas, Kafka Avro topic, existing Avro consumers, DLT/quarantine and audit evidence until formal retirement. A binary rollback that removes the new Outbox columns/reader is unsafe while any Avro row can be claimed.

## Validation evidence and hard stops

Run Maven reactor, the existing Schema Registry governance and Book Avro pilot CI, and real PostgreSQL/Kafka/Apicurio + Catalog + Recommendation/Engagement E2E. Required negative cases: pre-upgrade JSONB pending row survives, cache-cold Registry unavailable, ACK ambiguity, repeated physical publish, historical writer v1, v2 nullable snapshot, revision gap, malformed magic bytes/schema ID, raw DLT with source offset, consumer rebalance/seek and writer cutover while unfrozen. **A passing unit test or static Kustomize render is not a cutover rehearsal.**

Production gates still absent until measured/proven: real OIDC read-only token and 401/403, TLS/mTLS/CA, backup and restore of schema IDs, topic/Registry security across namespaces, actual rolling update in managed Kubernetes and read-model reconciliation. Do not call a GitHub merge a production rollout.

Links: [#349](https://github.com/Luca5Eckert/VellumHub/issues/349), [Schema Registry operations](SCHEMA_REGISTRY_OPERATIONS.md), [Book JSON baseline](BOOK_EVENTS.md), [ordered recovery](KAFKA_RECOVERY_RUNBOOK.md).
