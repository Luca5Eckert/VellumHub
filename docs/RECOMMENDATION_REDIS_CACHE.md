# Recommendation Redis response cache — #36

**Status (repository, 15 November 2026):** implementation merged in [PR #367](https://github.com/Luca5Eckert/VellumHub/pull/367); [issue #36](https://github.com/Luca5Eckert/VellumHub/issues/36) closed. The controlled [72,000-request A/B](https://github.com/Luca5Eckert/VellumHub/actions/runs/43650750262) and real Redis/Kafka tests passed, with workload-dependent gains in hot/high-concurrency scenarios and regressions on cold reads. **Not activated in production**; application default OFF, local Compose/Kind ON. [VH-REC](architecture/services/recommendation.md) · [C4 containers](architecture/diagrams/c4-containers.md) · [feedback sequence](architecture/flows/recommendation-feedback.md).

## Purpose and scope

`GET /recommendations?limit=&offset=` authenticates JWT/scopes at Recommendation's HTTP boundary and derives the canonical UUID from `sub`. A successful **MISS** uses the existing local pgvector ranker + PostgreSQL recommendation projection and serializes a `RecommendationResponse` DTO list into Redis. An authorized **HIT** returns only the DTO list; it does not invoke the ranker/repository. Empty results, errors and out-of-range pagination are not cached. The 70/30 ranker, HTTP contract, Kafka contracts and OAuth scopes are unchanged.

Redis is deliberately not used for event checkpoints, uniqueness, JWT/sessions, Schema Registry, or as a source of truth. Existing `recommendation.generation.duration` counts DB/rank generation on misses; `recommendations.requested` counts every HTTP success. The independent `vellumhub.recommendation.serving.duration` covers the full serving path, including a hit.

## Key and invalidation contract

- Versioned prefix: `vellumhub:recommendation:response:v1:ranker-70-30:`.
- Response key includes authenticated UUID, normalized `limit`/`offset`, random **global book generation** and random **per-user profile generation**.
- Response TTL default: **45 seconds** with up to ~20% expiry jitter; configurable 1–3600 s. Do not cache empty/failed responses.
- Global generation has no TTL; user generations have 24 h TTL. Both are UUID-based and are recreated with `SETNX` after loss. Old cache keys cannot be interpreted as current on key eviction/restart.
- Changing Book metadata, creating or deleting a Book, or recomputing a Book projection invalidates the **global** generation in the repository adapter.
- Saving a UserProfile after preferences/rating/reaction/reading-progress processing invalidates the **user's** generation in the adapter. The existing canonical JSON/Avro and legacy consumers call these repositories; duplicates guarded by checkpoints do not reach the save path. Some zero-adjustment/no-op saves may conservatively invalidate; this is an overhead, not a correctness failure.
- Under an active Spring transaction, invalidation registers `afterCommit`. Rollback does not invalidate. Without an outer transaction, the Spring Data repository call returns after its own transaction, and invalidation follows it.
- A slow request with generation `g` cannot populate a key for generation `g+1`. The response key contains both immutable generation tokens: a delayed write can only populate the unreachable **old key**, never a newer generation. Re-reading generations before each write would add network roundtrips without preventing an already in-flight stale response. An **already in-flight** response can still be stale; this is not linearizable caching.
- If Redis fails **after DB commit but before generation bump**, old responses can remain visible for at most the response TTL. A failed cache operation never rolls back a committed Kafka/DB effect; an alert/incident response is necessary if invalidation errors occur.

## Runtime and isolation

The Gateway continues using the existing Redis limiter. In Compose, the cache has its own `redis-recommendation-cache` instance. Local Kubernetes adds the same separate Service/StatefulSet. Both use `maxmemory 128mb` with `noeviction`; data is disposable, with no AOF or Redis persistence. If memory fills, cache operations fail open to PostgreSQL; memory and bypass/error rates must be monitored. Production ConfigMap defaults to **disabled** with a placeholder external cache host: set an approved managed endpoint, network ACL and `REDIS_RECOMMENDATION_PASSWORD` Secret before enabling. Do not enable against Gateway's rate-limiter Redis.

| Setting | Default | Meaning |
| --- | --- | --- |
| `RECOMMENDATION_CACHE_ENABLED` | `false` | Independent opt-in feature flag |
| `RECOMMENDATION_CACHE_TTL_SECONDS` | `45` | Maximum DTO TTL (with jitter) |
| `RECOMMENDATION_CACHE_REDIS_HOST` | `localhost` in app; Compose/K8s override | Isolated cache endpoint |
| `RECOMMENDATION_CACHE_REDIS_PORT` | `6379` | Cache port |
| `RECOMMENDATION_CACHE_REDIS_PASSWORD` | unset | Credential, supplied as Secret in Kubernetes |
| `vellumhub.cache.recommendations.max-limit` | `100` | Maximum request size eligible for cache |
| Spring Data Redis connect/read timeout | `150ms` | Fast bypass when Redis unhealthy |

The Redis health indicator is disabled because the cache is optional; JVM liveness/readiness stays anchored to required business dependencies. Rate limiter behavior and availability remain independent.

## Metrics and interpretation

- `vellumhub_cache_requests_total{cache="recommendations",result="hit|miss|bypass|error"}`.
- `vellumhub_cache_operation_duration_seconds{cache,operation}` (get, put, delete, generation, invalidate).
- `vellumhub_cache_invalidations_total{cache,scope="user|global",result}`.
- `vellumhub_cache_operation_errors_total{cache,operation}`.
- `vellumhub_recommendation_serving_duration_seconds{result}` with histogram.
- Also collect PostgreSQL query counters / slow SQL, HTTP request duration/error rate, JVM CPU/GC/heap, Redis memory/evictions, Kafka lag, and event→visible-ranking freshness. These are not interchangeable measures.
- Metric tags never contain username, user UUID, book ID, token, ISBN, request hash or raw Redis key. Logs should also avoid payload/keys. Cache hit ratio is `hit/(hit+miss)`; bypass and errors must be separately reported.

**Measurement scope:** the [72k reference](https://github.com/Luca5Eckert/VellumHub/actions/runs/43650750262) supports gains under hot keys and regressions under cold loads; **no production improvement or SLA is claimed**. Real traffic may differ due to Redis RTT and access distribution. Follow the [baseline and freshness harness](../services/recommendation-service/benchmark/README.md).

## Verification and evidence

The existing `Recommendation distributed benchmark` workflow runs:
1. Original event→projection and read-autonomy benchmark (AFF-REC-009).
2. Original event→visible ranking freshness (AFF-OPS-010).
3. Additional Redis Testcontainers integration and a **pre-warmed cache before Kafka rating** freshness check, including unauthenticated HTTP rejection.
4. A/B `OFF` and `ON` with a deterministic PostgreSQL/pgvector catalog (600 books, 400/1400/3500 deterministic user profiles depending on profile), authenticated MockMvc GET, and scenarios cold, warm, mixed. Each mode runs in a distinct Maven process with same commit/runner/fixtures. PR uses `smoke`; `workflow_dispatch` supports `reference` with 3 repetitions at concurrency 1/8/32 (1,000 requests per scenario), plus hot-key stampede; the dedicated PR #367 reference run covers cold, warm, 80/20 and 95/5 cache mixes.

Manual reference run (requires Docker, real Testcontainers and same SHA in separate runs):

```bash
SHA="$(git rev-parse HEAD)"
for MODE in false true; do
  mvn -B -ntp -pl services/recommendation-service -am \
    -Dtest=RecommendationCacheReadBenchmarkIT \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dgroups=cache-benchmark \
    -Dcache.benchmark.enabled="$MODE" \
    -Dbenchmark.profile=reference \
    -Dbenchmark.commitSha="$SHA" test
done
```

Files `services/recommendation-service/target/cache-benchmark/<sha>/off.json` and `on.json` contain raw per-request latency (no fixture UUIDs), p50/p95/p99, throughput, concurrency, scenario and seed. Keep full CI artifacts and include CPU/RAM/DB load and timestamps in any formal performance claim; reference benchmark is a controlled test, **not** production SLO/capacity. The A/B smoke is a correctness/signal gate, not a defensible resume metric.

## Acceptance, operational rollback, and remaining risks

1. Validate unit tests (HIT/MISS, DTO/identity isolation, malformed payload, after-commit, rollback, race, outage) and real Redis multi-client tests.
2. Validate Kafka+PostgreSQL+Redis prewarmed freshness and lack of synchronous upstream fan-out, preserving nDCG/ordering and security tests.
3. Inspect A/B raw evidence on the exact PR head, error rate and cache hit ratio. Publish a performance gain **only** when repeated reference workload supports it; no automatic assertion of a percentage.
4. Review pressure of global invalidations (new Book facts may rotate the epoch for every user), memory cap, hot-key stampedes across pods and operational credentials/TLS before enabling on production.
5. To roll back, set `RECOMMENDATION_CACHE_ENABLED=false` and restart the Recommendation deployment. This bypasses Redis without migration, flush or changes to Kafka/Outbox. **Never FLUSHALL on Gateway Redis**.

### Known limits

- The current implementation coalesces simultaneous MISSes for each versioned response key **within one JVM**, with an upper limit of 512 in-flight keys and a bounded 5-second follower wait. Distinct service replicas can independently lead one MISS per key; there is deliberately no distributed mutex or new Redis roundtrip on ordinary cold reads.
- Failed post-commit Redis invalidation is observable but not durably retried. Staleness is bounded by the configured response TTL, not guaranteed zero.
- User generation metadata grows only with recently cached users (24h expiry); global metadata is constant-size. Redis should be kept isolated from the rate-limiting workload.
- The completed 72k Testcontainers/MockMvc benchmark does **not** prove a production gain; do not present synthetic throughput as production. Production TLS/ACLs, canary, hit ratio and rollback require separate authorization.

Owner issue: [#36](https://github.com/Luca5Eckert/VellumHub/issues/36), completed via PR #367. Catalog [#366](https://github.com/Luca5Eckert/VellumHub/issues/366) also closed; hardening merged in [PR #370](https://github.com/Luca5Eckert/VellumHub/pull/370).

## Diagnostics of cold misses — 14 November 2026

The first reference measurement (head `68f3d67`, [GitHub Actions #43639474560](https://github.com/Luca5Eckert/VellumHub/actions/runs/43639474560)) with 600 books, 900 users and three independent 500-request repetitions per scenario revealed that an uncached response issues **12 prepared SQL statements**. This is consistent with a rank query, metadata hydration and lazy genre collection reads (N+1 candidate; confirm SQL categories before changing fetch behavior). Cache HIT bypasses all 12 SQL statements; MISS preserves all SQL work and adds synchronous Redis GET/SET roundtrips.

The original cache implementation performed two sequential generation GETs, a payload GET, two additional generation GETs after SQL and a response SET (plus SETNX for first-seen users). Because both generations are part of the immutable response key, those two post-SQL GETs are unnecessary for invalidation safety. A subsequent patch uses MGET for both generations and removes this redundant reread: hot HIT uses 2 Redis network roundtrips, an existing-version MISS 3, and a first-use user may require an extra SETNX. This is a performance **hypothesis under evaluation**; compare the post-change reference benchmark before claiming improvement.

Note that reference SQL counts use Hibernate statistics, and CPU/GC and Redis timings are diagnostics from a GitHub-hosted MockMvc integration run, **not** capacity or external-network HTTP performance.


### Repeated reference after generation MGET — intervention on #36

The optimized revision `753c8e80506318b6f0817df1294aa5d530befc04` passed the [same reference job #43640098145](https://github.com/Luca5Eckert/VellumHub/actions/runs/43640098145). This keeps dataset, ranker, profile, scenarios, three repetitions and concurrency settings identical to the previous run. **Different CI processes and host scheduling can still introduce run-to-run variability**; therefore, compare the stage-level evidence and not just a single p95.

Medians of the three within-run p95 measurements (milliseconds):

| Scenario | Concurrency | Original OFF / ON | Optimized OFF / ON |
| --- | ---: | ---: | ---: |
| Cold, 0% HIT | 1 | 3.82 / 6.78 | 3.77 / 6.66 |
| Cold, 0% HIT | 8 | 13.61 / 20.46 | 14.98 / 22.26 |
| Warm, 100% HIT | 8 | 16.54 / 3.11 | 11.43 / 4.79 |
| Mixed, 80% HIT | 8 | 14.80 / 20.84 | 14.31 / 16.68 |
| Mixed, 95% HIT | 8 | 14.02 / 8.43 | 14.84 / 8.65 |
| Mixed, 80% HIT | 32 | 55.75 / 35.92 | 53.04 / 35.79 |
| Hot-key stampede | 32 | 23.27 / 35.36 | 16.44 / 29.82 |

The **MGET intervention reduced total Redis generation-access time** for 500 mixed80/c8 requests from 1,219.70 to 698.80 ms (approximately 43%). Their cache-enabled p95 dropped 20.84→16.68 ms; the optimized OFF baseline nevertheless remains faster at 14.31 ms. Under mixed95/c8, optimized cache p95 was 8.65 ms against 14.84 ms OFF. At cold c8, optimized cache remains markedly slower (22.26 against 14.98 ms). These are workload-specific samples, not production SLAs or a guarantee of a 20% improvement across all traffic.

**Stampede evidence:** 32 synchronized requests for the same empty cache key produced 32 misses and **384 SQL statements** both before and after MGET, so the patch does not perform single-flight. That is a separate performance problem under concurrent bursts.

**Decision:** retain the feature flag `RECOMMENDATION_CACHE_ENABLED=false` and the PR in draft. Before activation, measure actual key reuse in a representative environment, investigate the existing 12-statement read (likely JPA lazy `@ElementCollection` N+1), and separately evaluate a bounded single-flight strategy under multi-replica tests. Optimize SQL regardless of whether Redis is retained. Do not conflate “a HIT is fast” with “mixed p95 improves.”


## Corrective intervention: genre N+1 and bounded single-flight (PR #367)

**Status:** submitted for CI validation; do not claim a measured improvement or enable the feature until the new SHA's JSON evidence is inspected. This intervention follows the reference findings above.

### SQL hydration (N+1)

The original `findAllById` loaded `Recommendation` entities but left `@ElementCollection genres` lazy; mapping a ranked page of ten DTOs could require ten additional SQL statements. The adapter now calls `findPageWithGenres(ids)`, a dedicated `select distinct r from Recommendation r left join fetch r.genres where r.bookId in :ids`. It retrieves a ranked **page already selected by pgvector**, not a paginated collection fetch join. `GetRecommendationsUseCase` still reorders the hydrated entities by their original ranking IDs, handles missing book IDs, and produces the same DTO shape.

The new `RecommendationBatchHydrationIT` uses real PostgreSQL via Testcontainers with **ten books, two genre rows per book**, asserts all 20 genres are available outside a persistence context and uses Hibernate statistics to require **exactly one SQL statement for hydration**. This eliminates the N+1 independently of cache activation. A typical request with rank query plus hydration should need approximately two SQL statements; confirm in the reference artifacts, and distinguish this expected count from measured numbers.

### Hot-key concurrent MISSes

`RecommendationResponseCache` coalesces simultaneous MISSes by the full versioned key using a `ConcurrentHashMap<String, CompletableFuture<List<RecommendationResponse>>>`. One leader invokes the ranker and populates Redis; other threads in **the same JVM** share the result. This avoids a distributed lock roundtrip on every miss. A semaphore limits the coordinator to 512 in-flight keys; waiters time out after five seconds and fall back to PostgreSQL rather than hanging indefinitely. Failed leaders propagate their error and remove the in-flight entry. `vellumhub.cache.singleflight.events` measures `leader`, `waiting`, `joined`, `timeout`, `capacity`, etc., with no user IDs in metric labels.

The in-flight key contains global/user generation tokens. A commit that invalidates an epoch sends new requests to a new key, without attaching them to a stale pending load. **Multiple replicas can each have a leader for the same key**; the limit is generally one computation *per replica*, not a global exactly-once guarantee. Two-real-client integration tests explicitly distinguish these semantics. Distributed locking is not silently claimed, nor added until production replica count and contention justify its additional RTTs and failure semantics.

### Stronger load and performance evidence

Each run is paired OFF/ON on the same SHA, Testcontainers versions, JVM/runner class, deterministic books and genuine cold/mixed user keys. Individual raw request times, p50/p95/p99, hit/miss, DB SQL prepared statements, ranker duration, Redis generation/get/put duration, CPU, GC, Redis memory deltas and coalescing counters are included.

| Profile | Requests per scenario, per repetition | Repetitions | Concurrency | Total OFF+ON measured scenario requests |
| --- | ---: | ---: | --- | ---: |
| `smoke` | 120 | 1 | 1, 8 | 1,920 |
| `reference` (PR #367) | 1,000 | 3 | 1, 8, 32 | 72,000 |
| `stress` (opt-in dispatch) | 3,000 | 3 | 8, 32, 64 | 216,000 |

The four cache mixes are `cold` (0%), `warm` (100%), `mixed80` (80%), and `mixed95` (95%). A separate synchronized hot-key stampede is measured for each concurrency/repetition with a SQL-budget assertion when Redis is enabled. The cold workload no longer cycles through fewer profile keys than its request count. Fixture population is sized to the profile (400/1,400/3,500 users; 600 books with two genres each).

Benchmark durations depend on the CI runner and 90-minute job timeout. Only **reference** is automatically selected for the focused PR #367; `stress` is opt-in via the existing workflow's `workflow_dispatch`. These are in-process authenticated MockMvc requests with real PostgreSQL/Redis, **not** independent HTTP network-level load tests, nor evidence of production hit ratio.

**Go/no-go:** the feature stays disabled by default until the current commit passes N+1/Redis/Kafka/CI checks and a representative workload shows acceptable p95/p99, CPU/SQL costs and freshness. If mixed80 remains slower, the appropriate engineering decision may still be **NO-GO**, even after eliminating N+1 and stampede.
