# Book schema evolution pilot — issue #347

**Scope and evidence:** isolated opt-in Maven module; does **not** change a deployed producer/consumer, `book-events`, Kafka ACLs, Catalog Outbox, or Recommendation projections. Creation of this module does not prove that a real production deployment has been tested. Acceptance requires green CI on the PR SHA.

- Decision / alternatives: [Vault ADR-020 and research, PR #29](https://github.com/Luca5Eckert/obsidian-vault/pull/29).
- Executable issue: [#347](https://github.com/Luca5Eckert/VellumHub/issues/347).
- Current (unchanged) JSON contract: [BOOK_EVENTS.md](BOOK_EVENTS.md) and [lib/kafka-contracts](../lib/kafka-contracts/).
- Existing JSON contract regression baseline: [AFF-CON-008, #324](https://github.com/Luca5Eckert/VellumHub/issues/324).

## How to run

Requires JDK 21, Maven, Docker with available network access to Maven Central, Confluent artifacts and container registries:

```sh
# Just the Avro tests (no containers)
mvn -B -ntp -Pschema-poc -pl integration-tests/schema-evolution-poc -am -DexcludedGroups=distributed test

# Complete pilot, including Kafka + Registry Testcontainers
mvn -B -ntp -Pschema-poc -pl integration-tests/schema-evolution-poc -am clean test
```

CI runs the complete command in `.github/workflows/schema-evolution-poc.yml`; Surefire reports are uploaded even on failure. This module is enabled only with Maven profile `schema-poc` and is absent from the normal reactor and service images. Maven codegen writes Java classes under `target/generated-sources/avro`, not into the existing manually maintained JSON `kafka-contracts` classes.

Pinned versions: JDK 21; Avro 1.12.1; Apicurio Registry/server/Java native client 3.1.7; Confluent Kafka Avro serializer 7.9.10; Kafka clients 3.9.1; Kafka Testcontainer image `confluentinc/cp-kafka:7.5.0`; Testcontainers 1.21.4; JUnit 5.11.4.

## Contract representation

- `src/main/avro/BookChangedEvent.avsc`: code-generated **v2 pilot** representation; public ECST fields from the current Java record, plus `catalogSource` with null default as a concrete compatible evolution exercise.
- `src/test/resources/history/book-v1.avsc`: historical writer schema without `catalogSource`.
- `src/test/resources/history/book-v2.avsc`: exact v2 contract fixture.
- `src/test/resources/history/book-v3-compatible.avsc`: v3 with another nullable field and default.
- `src/test/resources/history/book-v3-incompatible.avsc`: deliberately missing default for `catalogSource`, therefore a v3 reader can read v2 but **not v1**. Proves why last-version-only comparison is insufficient.

UUID and UTC timestamp are strings on the Avro wire for this PoC. Revision is Avro long, and `state` is `null|BookState`. The generated DTO is a **transport** contract and does not replace the domain type, validators or JPA entities.

Schema parsing canonical form strips defaults, aliases and other evolution metadata, so the codegen test additionally asserts the null defaults explicitly. Separate tests demonstrate a field rename with a reader alias and the failure of an older reader on a new enum symbol. These are contract-evolution examples, not a guarantee that arbitrary renames or enum changes are safe.

The Avro schema cannot itself encode the invariant that DELETED means `state=null` while CREATED/UPDATED/BASELINE must carry a snapshot; tests feed decoded Avro values through the current `BookChangedEvent` domain constructor to assert those semantics. Likewise a negative aggregate revision can be structurally valid long but invalid in the domain. An additional **test-only projection boundary** proves that changing Kafka's key from `bookId` to `eventId` would pass schema compatibility but must be rejected before a local read-model checkpoint is advanced. This is a semantic integration contract test, **not** an exercise of the actual Recommendation or Engagement consumer. `revision` is not a schema version.

Nullable metadata in the Avro contract is not intended as a statement that a particular catalog source stores null in every field. Refer to the live JSON contract for current behavior until the future migration.

## Registry and Kafka integration

The pilot uses a disposable Apicurio Registry container **without persistent storage**. Persistence/ACLs/CI preregistration belong to [#348](https://github.com/Luca5Eckert/VellumHub/issues/348).

1. Set compatibility to `BACKWARD_TRANSITIVE` in the isolated ccompat registry; register v1 and v2 for `book-events.schema-poc-com.vellumhub.kafka.avro.book.BookChangedEvent`.
2. Verify that a v3 candidate **without** `catalogSource` default is rejected, even though it can read v2; register the compatible v3.
3. Confluent `KafkaAvroSerializer` uses `TopicRecordNameStrategy`, explicit schema lookup (`auto.register.schemas=false`) and `/apis/ccompat/v7`. Its v1 `GenericRecord` travels through real Kafka; a v2 generated `SpecificRecord` consumer resolves the writer schema and fills `catalogSource=null`. The same producer then emits an actual generated v2 `SpecificRecord` **with automatic schema registration disabled** and `avro.remove.java.properties=true`, proving that the Git-registered v2 writer is resolvable without registering a Java-metadata variant.
4. Independently, Apicurio native `AvroKafkaSerializer` and `AvroKafkaDeserializer` use `/apis/registry/v3` and an isolated topic with automatic ephemeral artifact registration. The native serializer/consumer round trip is tested over Kafka; raw bytes are inspected and the native artifact ID is queried from the Registry. **The PoC does not assert cross-compatibility between native and Confluent wire formats**: their IDs and handlers are configuration-specific.
5. Subject naming: `TopicRecordNameStrategy` is deliberately selected and the actual subject is checked via `/subjects`, because future `engagement.ratings.v1` hosts multiple record types. The native resolver is checked separately and uses its default topic-value artifact convention. This is a PoC finding to confirm for additional types and native strategies before enabling production consumers.

### Selection rationale

Prefer **Confluent ccompat SerDes on Apicurio** for a future standardized Kafka wire format **if and only if** the end-to-end v1→v2 and transitive registry checks pass: it has an explicit `TopicRecordNameStrategy` and a conventional, inspectable 5-byte framing (magic byte + schema ID) for Kafka Avro. Both formats are inspected with a raw Kafka `ByteArrayDeserializer`: assert magic byte + 4-byte identifier, cross-check the Confluent IDs with the IDs returned when registering v1/v2, and check the Apicurio-native artifact identity. **Identical framing lengths alone do not imply interchangeable schema IDs, policies or consumers.** Native Apicurio remains a tested, viable alternative; no cross-format consumption is claimed. This avoids mixing runtime serializer conventions by accident. Comparison is functional and qualitative, **not** a throughput or latency benchmark.

## Out-of-scope and release gates

- **Not implemented:** persistent Registry, governance/secrets, read-only schema lookup permissions, cross-service cutover, migration of pending JSONB Outbox records, v2 publishing in Catalog, CDC/Debezium, Redis Streams.
- **No promised guarantee:** zero downtime, exactly-once physical delivery, universal DLT replay or production rollout.
- **Required before #348/#349:** PR CI green on the exact head SHA, generated v2 writer/reader round trip verified with auto-register disabled, Registry rejects transitive incompatible schema, raw Kafka framing/ID and actual subjects/artifacts verified for both SerDes, and documented limitations of semantic evolution.
- If Apicurio ccompat differs from Confluent behavior, keep #347 open and reconcile [ADR-020](https://github.com/Luca5Eckert/obsidian-vault/pull/29) rather than weaken compatibility.

## Official references

- [Apache Avro specification — schema resolution](https://avro.apache.org/docs/current/specification/)
- [Apicurio 3.1 Kafka SerDes configuration](https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/assembly-configuring-kafka-client-serdes.html)
- [Apicurio ccompat v7 API](https://www.apicur.io/registry/docs/apicurio-registry/3.1.x/getting-started/assembly-confluent-schema-registry-compatibility.html)
- [Confluent — schema compatibility modes](https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html)

## Code review traceability (06/11/2026)

The follow-up review of [PR #351](https://github.com/Luca5Eckert/VellumHub/pull/351) identified missing tests for generated v2 publishing, on-wire evidence, evolution metadata, semantic Kafka-key changes and active/null-state cases. The branch adds these checks before merge. Evidence is valid only for the **latest** branch SHA that has passing CI; the previous green run applies to an earlier SHA and must not be reused as proof of these fixes.

Scope boundary: a test-only read model guards the semantic key invariant; applying the same guard inside real consumers and maintaining safe outbox/cutover remain the subject of #349, not a claim of this pilot.
