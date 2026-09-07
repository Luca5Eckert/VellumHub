# Distributed Recommendation Benchmark

This harness implements VellumHub issue #283. It measures three properties of the Recommendation architecture through real distributed boundaries:

1. how quickly a Kafka interaction signal converges into Recommendation-owned projection state;
2. how quickly that interaction is reflected by the authenticated recommendation endpoint;
3. whether recommendation reads continue to work from materialized local state while User, Catalog, and Engagement are unavailable.

The result is a reproducible local/integration benchmark. It is **not** a production SLA/SLO, an exactly-once claim, or a maximum-capacity test.

## Boundaries

The benchmark uses:

- real Kafka through `confluentinc/cp-kafka:7.5.0` Testcontainers;
- real PostgreSQL + pgvector through `pgvector/pgvector:pg15`;
- the real Flyway schema;
- the production `CreatedRatingConsumerEvent` and user-profile update path;
- the production recommendation ranking/repository path;
- the authenticated `/recommendations` HTTP endpoint through Spring Security + MockMvc.

Only test-fixture embeddings are deterministic. Model inference is intentionally outside the architectural propagation/freshness measurements.

No event/user/book IDs are added to production Prometheus labels. Per-event correlation exists only in benchmark state and exported raw evidence.

## Projection convergence protocol

The reference benchmark uses `CreatedRatingEvent` because it is a representative interaction signal that updates the local `user_profiles` projection.

Each benchmark event receives a deterministic, unique test user ID:

```text
t0 = immediately before Kafka publication from the benchmark producer
t1 = first observation of that unique user profile in Recommendation PostgreSQL
event-to-projection latency = t1 - t0
```

The book feature required by the profile update is pre-materialized in Recommendation so embedding/model work does not contaminate the propagation measurement.

The harness records submitted/projected/failed events, unexpected DLT records, observed events/s, p50/p95/p99 projection latency, maximum sampled consumer lag, catch-up time, and raw per-event timings.

Projection state is polled every 10 ms, so measured latency includes that observation granularity.

## Event-to-recommendation freshness protocol

`RecommendationFreshnessBenchmarkIT` measures the user-visible consequence of the event-driven architecture rather than stopping at projection state.

The deterministic fixture contains:

- popular decoy books that dominate the cold/fallback ranking;
- a signal book with an embedding on a known vector axis;
- a low-popularity semantic target aligned with that signal book.

Before the interaction, `/recommendations` must not return the semantic target at rank #1. The harness then publishes a real `CreatedRatingEvent` and polls the authenticated HTTP endpoint until the production ranking query returns that target at rank #1.

```text
t0 = immediately before CreatedRatingEvent publication
t1 = first successful authenticated /recommendations response where rank #1 reflects the interaction
event-to-recommendation freshness = t1 - t0
```

This path therefore includes Kafka publication/consumption, profile update, PostgreSQL/pgvector state, the production semantic+popularity ranking query, mapping/security, and HTTP serving.

The benchmark reports p50/p95/p99 freshness plus full reflection/convergence counts. Polling uses 10 ms granularity. Burst scenarios use round-robin HTTP observation across unique users, so the measurement deliberately includes benchmark observation overhead; retained CI values are evidence for controlled runs, not production SLA values.

## Load profiles

`reference` supports three scenarios in both convergence and freshness harnesses:

| Scenario | Default events | Batch size | Behavior |
|---|---:|---:|---|
| `sequential` | 10 | 1 | publish one event and observe completion before continuing |
| `small-burst` | 30 | 10 | publish in small batches |
| `medium-burst` | 90 | 30 | publish in larger batches |

Defaults are deliberately modest. They exercise convergence, backlog and recommendation freshness; they do not claim machine capacity.

CI uses `benchmark.profile=smoke`, which executes the same architectural paths with a very small event/read set and asserts correctness invariants only.

## Read-autonomy protocol

The benchmark materializes a local recommendation catalog via real `created-book` Kafka events, then creates a user profile through a real rating event.

User, Catalog, and Engagement are intentionally **not started** by the distributed test stack; their test endpoints are configured to an unreachable loopback address. The benchmark then performs warm-up authenticated reads, executes a fixed measured batch, records success rate and p50/p95/p99 latency, and verifies that all reads succeed from Recommendation-owned state.

The test is specifically about serving already-materialized reads. It does not claim that new upstream events can arrive while their producers are down.

## Run the reference benchmark

Docker must be available. From the repository root:

```bash
mvn -pl services/recommendation-service -am \
  -Dtest=DistributedRecommendationBenchmarkIT,RecommendationFreshnessBenchmarkIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dgroups=distributed-benchmark \
  -Dbenchmark.profile=reference \
  test
```

To run the CI-sized harness locally, replace `reference` with `smoke`.

`failIfNoSpecifiedTests=false` is required because `-am` builds `kafka-contracts` first and that module does not contain the selected Recommendation tests.

## Output

The default reference output is:

```text
services/recommendation-service/target/distributed-benchmark/reference/
  run-metadata.json
  raw-results.json
  summary.md
  recommendation-freshness.json
  recommendation-freshness.md
```

The original files retain projection/read-autonomy evidence. `recommendation-freshness.json` stores raw and aggregate event-to-recommendation measurements, while `recommendation-freshness.md` provides the human-readable freshness result.

## CI and reference evidence

The `Recommendation distributed benchmark` workflow runs `smoke` automatically on relevant pull requests and supports `reference` (or `smoke`) on manual dispatch. The workflow uploads the complete output directory as a versioned GitHub Actions artifact.

CI intentionally has no absolute p95, throughput, capacity, or freshness threshold. It fails only when correctness invariants fail, such as projection loss, unexpected DLT, sustained lag beyond the harness timeout, failed recommendation reads, or an interaction that never becomes visible in the deterministic recommendation ranking.

## Interpretation guardrails

- Local/Testcontainers results are not production capacity.
- Event and freshness latency include benchmark polling/observation granularity.
- Consumer lag is sampled by the harness, so a very short transient peak can occur between samples.
- Read autonomy means no synchronous domain dependency is needed for already-materialized recommendation reads.
- It does not imply full-platform availability while upstream systems are down.
- No exactly-once claim is made.
- The strongest public latency claim is the retained **event-to-recommendation freshness p95**, because it measures the complete interaction-to-changed-ranking path rather than adding separately measured projection and read percentiles.
- Quantitative resume/portfolio claims should use only values from a retained reference artifact tied to a commit SHA.
