# Distributed Recommendation Benchmark

This harness implements VellumHub issue #283. It measures two properties of the Recommendation architecture through real distributed boundaries:

1. how quickly a Kafka interaction signal converges into Recommendation-owned projection state;
2. whether recommendation reads continue to work from materialized local state while User, Catalog, and Engagement are unavailable.

The result is a reproducible local/integration benchmark. It is **not** a production SLA/SLO, an exactly-once claim, or a maximum-capacity test.

## Boundaries

The benchmark uses:

- real Kafka through `confluentinc/cp-kafka:7.5.0` Testcontainers;
- real PostgreSQL + pgvector through `pgvector/pgvector:pg15`;
- the real Flyway schema;
- the production `CreatedRatingConsumerEvent` and user-profile update path;
- the production recommendation ranking/repository path;
- the authenticated `/recommendations` HTTP endpoint through Spring Security + MockMvc.

Only the embedding provider used while materializing book projections is deterministic. Embedding inference is not part of the propagation or read-autonomy property being measured.

No event/user/book IDs are added to production Prometheus labels. Per-event correlation exists only in benchmark state and exported raw evidence.

## Propagation protocol

The reference benchmark uses `CreatedRatingEvent` because it is a representative interaction signal that updates the local `user_profiles` projection.

Each benchmark event receives a deterministic, unique test user ID:

```text
t0 = immediately before Kafka publication from the benchmark producer
t1 = first observation of that unique user profile in Recommendation PostgreSQL
event-to-projection latency = t1 - t0
```

The book feature required by the profile update is pre-materialized in Recommendation so embedding/model work does not contaminate the propagation measurement.

The harness records:

- submitted/projected/failed events;
- unexpected DLT records;
- observed projected events/s;
- event-to-projection p50/p95/p99;
- maximum observed lag for the Recommendation consumer group on `created-rating`;
- catch-up time from the last publication until committed consumer lag returns to zero;
- raw per-event timings.

Projection state is polled every 10 ms, so measured latency includes that observation granularity.

## Load profiles

`reference` supports the three required scenarios:

| Scenario | Default events | Batch size | Behavior |
|---|---:|---:|---|
| `sequential` | 10 | 1 | publish one event and wait for its projection |
| `small-burst` | 30 | 10 | publish in small batches |
| `medium-burst` | 90 | 30 | publish in larger batches |

Defaults are deliberately modest. They exercise convergence and lag behavior; they do not claim machine capacity.

The parameters are overridable:

```text
benchmark.sequential.events
benchmark.sequential.pauseMs
benchmark.small.events
benchmark.small.batchSize
benchmark.small.pauseMs
benchmark.medium.events
benchmark.medium.batchSize
benchmark.medium.pauseMs
benchmark.read.warmup
benchmark.read.requests
```

CI uses `benchmark.profile=smoke`, which executes the same harness with a very small event/read set and asserts only correctness invariants.

## Read-autonomy protocol

The benchmark materializes a local recommendation catalog via real `created-book` Kafka events, then creates a user profile through a real rating event.

User, Catalog, and Engagement are intentionally **not started** by the distributed test stack; their test endpoints are configured to an unreachable loopback address. The benchmark then:

1. performs warm-up authenticated reads against `/recommendations`;
2. executes a fixed measured read batch;
3. records HTTP success rate and p50/p95/p99 latency;
4. verifies all measured reads succeed from Recommendation-owned state.

The test is specifically about serving already-materialized reads. It does not claim that new upstream events can arrive while their producers are down.

## Run the reference benchmark

Docker must be available.

From the repository root:

```bash
mvn -pl services/recommendation-service -am \
  -Dtest=DistributedRecommendationBenchmarkIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dgroups=distributed-benchmark \
  -Dbenchmark.profile=reference \
  test
```

To run the CI-sized harness locally:

```bash
mvn -pl services/recommendation-service -am \
  -Dtest=DistributedRecommendationBenchmarkIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dgroups=distributed-benchmark \
  -Dbenchmark.profile=smoke \
  test
```

`failIfNoSpecifiedTests=false` is required because `-am` builds `kafka-contracts` first and that module does not contain the selected Recommendation test.

## Output

The default reference output is:

```text
services/recommendation-service/target/distributed-benchmark/reference/
  run-metadata.json
  raw-results.json
  summary.md
```

`run-metadata.json` records commit SHA, benchmark profile, scenario parameters, seed, event/topic/group, read warm-up/measurement counts, Java/OS/CPU context, command, and protocol notes.

`raw-results.json` keeps individual event and request measurements plus aggregate scenario/autonomy results.

`summary.md` is the human-readable evidence report.

## CI and reference evidence

The `Recommendation distributed benchmark` workflow runs:

- `smoke` automatically on relevant pull requests;
- `reference` (or `smoke`) on manual dispatch.

The workflow uploads the complete output directory as a versioned GitHub Actions artifact.

CI intentionally has no absolute p95, throughput, or capacity threshold. It fails only when correctness invariants fail, such as projection loss, unexpected DLT, sustained lag beyond the harness timeout, or failed recommendation reads.

## Interpretation guardrails

- Local/Testcontainers results are not production capacity.
- Event latency includes the benchmark polling interval.
- Consumer lag is sampled by the harness, so a very short transient peak can occur between samples.
- Read autonomy means no synchronous domain dependency is needed for already-materialized recommendation reads.
- It does not imply full-platform availability while upstream systems are down.
- No exactly-once claim is made.
- Quantitative resume/portfolio claims should use only values from a retained reference artifact tied to a commit SHA.
