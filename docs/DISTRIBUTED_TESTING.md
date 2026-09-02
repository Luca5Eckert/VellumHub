# Distributed Integration Testing

VellumHub uses Testcontainers to validate failure-sensitive distributed behavior against real infrastructure. Issue #207 introduces the Recommendation Service as the pilot for Kafka + PostgreSQL/pgvector integration tests.

The suite is intentionally narrower than a platform end-to-end test. It validates one service boundary deeply and leaves multi-service orchestration to a dedicated follow-up.

## What the pilot proves

The Recommendation Service distributed suite covers two `created-book` paths:

1. **Successful projection**
   - a `CreateBookEvent` is serialized with the same JSON serializer and type mapping used by the Catalog Service;
   - the event is published to a real Kafka broker;
   - the real Recommendation Kafka listener consumes it;
   - the real application use cases execute transactionally;
   - the projection is persisted in a real PostgreSQL 15 database with pgvector;
   - assertions verify `book_features`, the 384-dimensional vector, `recommendations`, and recommendation genres.

2. **Retry and DLT**
   - a scoped application failure is injected into the real `created-book` listener path;
   - the listener is invoked three times, matching the production retry policy;
   - retry backoff is reduced to 100 ms only for the distributed test runtime;
   - Kafka retry topics and DLT routing remain real;
   - a separate Kafka consumer observes the final `created-book-dlt` record;
   - the database is verified to contain no partial projection for the failed event.

Production behavior is unchanged: `KafkaRetryConfig` still defaults to three attempts with a fixed 3000 ms backoff. The test override changes only elapsed time, not retry semantics.

## Real dependencies and controlled test doubles

The suite deliberately keeps infrastructure and persistence real.

| Boundary | Test behavior |
|---|---|
| Kafka broker | Real `confluentinc/cp-kafka:7.5.0` Testcontainer |
| PostgreSQL | Real `pgvector/pgvector:pg15` Testcontainer |
| Flyway migrations | Real; schema is migrated before assertions |
| Kafka serialization | Real Spring Kafka `JsonSerializer`, aligned with Catalog `CreateBookEvent` type mapping |
| Recommendation Kafka listener | Real |
| Retry topics / DLT routing | Real |
| JPA / Hibernate vector persistence | Real |
| Embedding model | Mocked to a deterministic 384-dimensional vector |
| Failure injection | Spy on `CreateBookFeatureUseCase`, scoped to one test event ID |

The embedding model is outside the behavior being proven here and is relatively expensive/non-deterministic compared with a fixed fixture. Mocking that port keeps the test focused on delivery, transaction boundaries, retry, DLT, and persistence.

The retry test does not mock Kafka or invoke the listener directly. It only injects a controlled application failure so that Spring Kafka's real retry/DLT machinery can be observed.

## Reusable fixture

`DistributedIntegrationTestSupport` owns the Recommendation Service pilot infrastructure:

- shared pgvector PostgreSQL container;
- shared Kafka container;
- dynamic Spring datasource and Kafka properties;
- producer serialization compatible with the Catalog contract;
- deterministic asynchronous timeouts;
- test-only retry backoff override.

New Recommendation distributed tests should extend this support rather than duplicate container configuration.

This support is intentionally service-local for #207. Extracting a cross-service testing library before two or more services prove the same abstraction would create premature coupling. A follow-up issue tracks the shared harness and true cross-service E2E evolution.

## Asynchronous assertions

Do not use `Thread.sleep` to wait for Kafka or database state.

Use Awaitility/eventual assertions with:

- a bounded timeout;
- a short poll interval;
- assertions against externally observable state where possible.

The pilot uses a 15-second assertion timeout and 100 ms polling. These are safety bounds, not expected steady-state durations.

## Idempotency and transactional outbox

At the time #207 is implemented, idempotent-consumer and transactional-outbox behavior are documented as reliability-hardening goals, but no concrete Recommendation implementation exists to exercise.

Therefore this PR does **not** invent production idempotency or outbox semantics merely to satisfy a test checklist. When those mechanisms are implemented, the distributed suite should add cases that prove, at minimum:

- duplicate delivery does not apply the same state transition twice;
- an outbox record and domain state commit atomically;
- publication/republication preserves the intended delivery guarantees;
- recovery after producer/consumer failure does not lose committed work.

The follow-up distributed-testing issue should remain aligned with the feature issues that introduce those guarantees.

## Local execution

Prerequisites:

- JDK 21;
- Maven or the repository Maven wrapper where applicable;
- Docker Desktop running on Windows, or a compatible Docker Engine;
- enough Docker resources to run Kafka and PostgreSQL concurrently.

No locally installed Kafka or PostgreSQL instance is required.

From the repository root, first verify Docker:

```powershell
docker info
```

Then run only the distributed Recommendation suite:

```powershell
mvn -pl services/recommendation-service -am -Dgroups=distributed test
```

To run the normal reactor verification while excluding the distributed tag:

```powershell
mvn -DexcludedGroups=distributed clean verify
```

A normal unfiltered Maven test run may include the distributed tests and therefore requires Docker.

## CI behavior and execution budget

Pull-request validation keeps distributed tests visible as their own job:

- `Maven verify` runs the non-distributed reactor tests;
- `Recommendation distributed integration` pre-pulls Kafka, pgvector, and Ryuk images;
- Maven dependencies/test compilation are warmed before timing begins;
- the tagged distributed suite runs independently;
- its measured execution budget is 45 seconds after warm-up;
- the job itself has a five-minute hard timeout for infrastructure failures;
- image build/scan waits for both Maven verification and distributed integration to pass.

Separating the jobs makes failures attributable: a unit/slice regression is not confused with Kafka/Testcontainers startup or distributed behavior.

## Extension rules

When expanding the suite:

1. Test behavior at a real boundary, not framework configuration alone.
2. Prefer the real database engine when constraints, transactions, extensions, or SQL semantics matter.
3. Prefer the real broker when delivery, serialization, retry, ordering, headers, or DLT semantics matter.
4. Mock only out-of-scope or non-deterministic edges; do not mock the boundary under test.
5. Give events, consumer groups, and records unique identifiers so tests remain isolated.
6. Use eventual assertions instead of fixed sleeps.
7. Assert durable/external outcomes before internal call counts. Call counts are appropriate only when the retry contract itself is the behavior under test.
8. Keep failure injection local to the test and restore state automatically through Spring's test context.
9. Keep the suite fast enough to run before opening a PR; slow platform journeys belong in a separate E2E layer.
10. Add a test only when it protects an explicit reliability guarantee.

## E2E boundary

The #207 pilot publishes through a test `KafkaTemplate` configured to match the Catalog Service's `CreateBookEvent` serialization. It does **not** boot the Catalog Service or instantiate its real `KafkaBookEventProducer` in the same test.

A true cross-service path such as:

```text
Catalog KafkaBookEventProducer
  -> Kafka
  -> Recommendation CreateBookConsumerEvent
  -> PostgreSQL/pgvector
```

belongs in the follow-up E2E/shared-harness work. That keeps #207 consistent with its explicit exclusion of a full multi-service E2E while still proving the Recommendation distributed boundary with real infrastructure.

## Relationship to other tests

This suite complements rather than replaces:

- unit tests for domain/application behavior;
- MVC/controller slice tests;
- repository tests;
- Flyway PostgreSQL/pgvector migration tests;
- future platform E2E tests.

Use the narrowest test level that can prove the behavior. Reserve distributed Testcontainers tests for contracts and failure modes whose correctness depends on the real infrastructure boundary.
