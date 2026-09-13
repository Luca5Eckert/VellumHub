# Distributed Integration Testing

VellumHub uses Testcontainers for reliability-sensitive flows whose correctness depends on real Kafka or PostgreSQL/pgvector behavior. The shared harness introduced by issue #278 evolves the Recommendation pilot from #207 only after a second concrete consumer exists: the Catalog producer distributed test.

## Current coverage

The distributed suite now proves three concrete paths.

### Catalog producer -> Kafka

`KafkaBookEventProducerDistributedTest` instantiates the production `KafkaBookEventProducer`, publishes a `CreateBookEvent` to a real Kafka broker, and inspects the emitted record with the shared `KafkaProbe`.

The test verifies:

- the production producer implementation is used;
- the production `create_book_event` type mapping is represented in the Kafka type header;
- key and JSON payload cross the real broker boundary;
- no locally installed Kafka is required.

### Catalog producer -> Recommendation projection

The Recommendation distributed happy path is a cross-service contract test:

```text
Catalog KafkaBookEventProducer
  -> real Kafka
  -> Recommendation CreateBookConsumerEvent
  -> Recommendation application use cases
  -> real PostgreSQL/pgvector
```

The source service is not fully booted. The test deliberately instantiates the real Catalog producer class with the KafkaTemplate connected to the shared test broker, then boots the real Recommendation Spring context. This keeps the scenario focused on the producer/consumer contract and durable projection instead of turning the test into a full platform journey.

The durable assertions cover `book_features`, the 384-dimensional pgvector embedding, `recommendations`, genres, title, author, and initial popularity.

### Recommendation retry -> DLT

The failure path still exercises the real Recommendation listener, Kafka retry topics, and DLT routing. A scoped spy failure is injected for one event ID; the test verifies three attempts, observes the DLT record through the shared Kafka probe, inspects the original-topic header, and asserts that no partial projection remains in PostgreSQL.

## Shared test-support module

`lib/distributed-test-support` is intentionally small. It contains only abstractions already proven useful in multiple concrete tests:

- `KafkaIntegrationTestSupport` — standard Kafka Testcontainer image and asynchronous timing bounds;
- `KafkaPgvectorIntegrationTestSupport` — adds the standard pgvector/PostgreSQL Testcontainer;
- `BookEventFixtures` — stable `CreateBookEvent` fixtures that keep irrelevant event fields out of individual tests;
- `KafkaProbe` — unique consumer groups, bounded polling, record filtering, and UTF-8 header inspection.

Spring Boot configuration remains service-local. Catalog currently uses Spring Boot 3 while Recommendation uses Spring Boot 4, so pushing `DynamicPropertyRegistry`, application properties, or service-specific beans into the shared module would couple the harness to framework details instead of infrastructure behavior.

## Container lifecycle and state isolation

Containers are static per test-support hierarchy and are reused for the lifetime of a test class/JVM. They are not configured for daemon-style reuse across Maven or CI runs. This saves startup work inside a suite while ensuring a fresh environment for a new test process.

State isolation is explicit:

- event IDs use fresh UUIDs;
- Kafka probes create a unique consumer group and client ID per probe;
- Recommendation persistence is cleared before each distributed scenario;
- assertions filter records by topic and event key rather than assuming an empty broker;
- production topics remain unchanged because the real listeners are bound to the production contract names.

Container reuse must not be widened across CI jobs or independent Maven executions unless deterministic cleanup is demonstrated first.

## Asynchronous assertions

Do not use `Thread.sleep` in distributed tests.

For application state, use Awaitility with an explicit upper bound and polling interval. For raw Kafka inspection, use `KafkaProbe.awaitRecord`, which polls the broker until the same explicit deadline expires.

Current safety bounds are:

- assertion timeout: 15 seconds;
- assertion poll interval: 100 ms.

These are failure bounds, not expected steady-state latency targets.

## Real dependencies and controlled doubles

| Boundary | Test behavior |
|---|---|
| Kafka broker | Real `confluentinc/cp-kafka:7.5.0` Testcontainer |
| PostgreSQL | Real `pgvector/pgvector:pg15` Testcontainer |
| Flyway migrations | Real in Recommendation distributed tests |
| Catalog `KafkaBookEventProducer` | Real production class |
| Kafka serialization / type headers | Real Spring Kafka serializer |
| Recommendation Kafka listener | Real |
| Retry topics / DLT | Real |
| JPA / Hibernate vector persistence | Real |
| Embedding model | Deterministic test double |
| Failure injection | Scoped spy on the Recommendation use case |

The embedding model is outside the Kafka delivery and persistence guarantee being exercised. Keeping it deterministic prevents model execution from obscuring broker, retry, transaction, or pgvector failures.

## Local execution

Prerequisites:

- JDK 21;
- Maven;
- Docker Desktop or a compatible Docker Engine.

No local Kafka or PostgreSQL installation is required.

Run the full distributed/E2E slice from the repository root:

```bash
mvn -pl services/catalog-service,services/recommendation-service -am -Dgroups=distributed test
```

Run normal reactor verification without Docker-backed distributed scenarios:

```bash
mvn -DexcludedGroups=distributed clean verify
```

A non-filtered Maven test run can execute `@Tag("distributed")` tests and therefore requires Docker.

## CI execution budget

Pull-request validation keeps distributed failures separate from ordinary Maven verification.

The distributed job:

1. verifies Docker availability;
2. pre-pulls Ryuk, Kafka, and pgvector images;
3. warms reactor dependencies and test compilation;
4. runs the Catalog and Recommendation distributed modules together;
5. enforces a 75-second post-warm execution budget;
6. has a five-minute hard job timeout for infrastructure failures.

The 75-second budget accounts for the second real service context while remaining a guard against accidental expansion into a slow platform E2E suite.

If the post-warm distributed run becomes unstable near the budget, shard by infrastructure cost rather than by arbitrary test count: keep the Kafka-only Catalog producer suite in one job and the Kafka + pgvector Recommendation suite in another. Each shard must retain its own explicit budget.

## Adding a new distributed scenario

Use the narrowest shared support that matches the boundary:

1. Extend `KafkaIntegrationTestSupport` for Kafka-only behavior.
2. Extend `KafkaPgvectorIntegrationTestSupport` when pgvector/PostgreSQL semantics are part of the guarantee.
3. Add a fixture to the shared module only when at least two concrete tests benefit from the same abstraction.
4. Give every event and probe a unique identity.
5. Prefer durable/external outcomes over implementation call counts.
6. Use a call-count assertion only when retry cardinality itself is the contract under test.
7. Use `KafkaProbe` for raw publication, DLT, and header assertions instead of duplicating consumer configuration.
8. Keep service-specific Spring wiring in that service's test source set.
9. Do not add another infrastructure dependency until a production guarantee requires it.

## Troubleshooting

If a distributed test fails before application assertions, check Docker and container startup first. The CI job pre-pulls the same images used locally to make image/network failures distinguishable from application regressions.

If Kafka records are not observed:

- confirm the event key is unique and matches the probe filter;
- confirm the producer type mapping matches the consumer contract;
- inspect the DLT before increasing timeouts;
- do not replace bounded polling with fixed sleeps.

If Recommendation persistence fails:

- confirm Flyway completed against the pgvector container;
- inspect the event consumer error before changing database assertions;
- keep the deterministic embedding at 384 dimensions so vector-schema failures remain visible.

## Idempotency and transactional outbox

Consumer idempotency and transactional outbox publication are still planned production guarantees, not implemented guarantees. Therefore issue #278 does not invent them merely to add tests.

When their production mechanisms exist, extend this suite to prove:

- duplicate delivery applies a durable effect once;
- retry/reprocessing preserves that guarantee;
- domain state and outbox records commit atomically;
- committed outbox events survive publisher failure and are republished;
- a successful domain commit does not lose its corresponding event.

## Explicit limits

The shared harness is not a general testing framework and does not replace unit, slice, repository, migration, or REST contract tests. It is reserved for behavior that depends on a real distributed boundary.

Toxiproxy, network latency/fault injection, metrics-level retry assertions, and broader parallel execution remain conditional improvements. Add them only when a concrete production failure mode or guarantee makes the extra infrastructure worthwhile.
