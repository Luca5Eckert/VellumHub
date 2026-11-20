# Schema evolution tests

`integration-tests/schema-evolution-poc` is an opt-in Maven module (profile `schema-poc`, workflow `.github/workflows/schema-evolution-poc.yml`) that proves the Avro evolution rules the Registry relies on. It is not part of the default reactor or of any service image. Production contracts live in [`lib/kafka-avro-contracts`](../lib/kafka-avro-contracts); operation of the Registry is in [Schema Registry operations](SCHEMA_REGISTRY_OPERATIONS.md); per-stream switching is in the [wire format state machine](architecture/states/event-wire-format.md).

```sh
# Avro rules only (no containers)
mvn -B -ntp -Pschema-poc -pl integration-tests/schema-evolution-poc -am -DexcludedGroups=distributed test
# Including Kafka + Apicurio Testcontainers
mvn -B -ntp -Pschema-poc -pl integration-tests/schema-evolution-poc -am clean test
```

## What the tests prove

| Rule | Evidence in the module |
| --- | --- |
| `BACKWARD_TRANSITIVE`, not last-version-only | Book fixtures `book-v1` … `book-v3-incompatible`: the incompatible v3 reads v2 but **not** v1, and the Registry rejects it |
| Every canonical record has a compatible and an incompatible v2 | `*-v2-compatible.avsc` / `*-v2-incompatible.avsc` for reading progress and each rating and reaction event |
| New fields need defaults | Codegen test asserts null defaults explicitly (canonical form strips them) |
| Explicit schema lookup | Confluent ccompat serializer with `TopicRecordNameStrategy`, `auto.register.schemas=false`, `avro.remove.java.properties=true`; v1 writer read by a v2 reader over real Kafka |
| Wire framing | Raw bytes inspected: magic byte + 4-byte schema ID matching the registered IDs |
| Renames and enums | Reader alias handles a field rename; an old reader fails on a new enum symbol |
| Semantics the schema cannot express | Decoded values go through the domain constructor: DELETED ⇒ `state=null`, others carry a snapshot, revision ≥ 0 |
| Key stability | Changing the Kafka key from `bookId` to `eventId` passes schema compatibility but is rejected before a checkpoint advances |

`revision` is the aggregate state revision, not a schema version. The Apicurio-native SerDes path is also exercised, but no cross-format compatibility between native and Confluent framing is claimed. The tests use a disposable, non-persistent Registry; they make no claim about deployed Registries, ACLs or throughput.
