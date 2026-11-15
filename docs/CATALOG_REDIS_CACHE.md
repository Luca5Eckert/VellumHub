# Catalog Redis read cache — implementation, operation and evidence

**Scope:** [issue #366](https://github.com/Luca5Eckert/VellumHub/issues/366), [PR #369](https://github.com/Luca5Eckert/VellumHub/pull/369). The cache accelerates authorized `GET /books/{id}` and `GET /books?pageNumber=&pageSize=` without modifying HTTP DTOs, Catalog ownership, Book Outbox, Kafka contracts or book-cover binaries.

## Architecture and correctness

- `BookReadCache` caches immutable response DTOs using JSON. Each book has its own random generation token with 24h retention; pages share a global random generation token with no TTL. Redis is configured **noeviction**, volatile/no persistence for local development. In-memory PostgreSQL query state is never used as an authority.
- Cache keys are versioned (`vellumhub:catalog:response:v1:`) and include book UUID or `pageNumber/pageSize` and `createdAt-id` sort version, plus generation. A read started before an update can only fill its old generation key; it cannot repopulate the new key after a successful invalidation.
- `OutboxBookChangeWriter.record` is the single mutation boundary; after an outbox append, the cache registers an **afterCommit** invalidation. This covers create, ISBN import, update, delete/soft-delete, approval of a Book request and both cover metadata paths. A no-op update without an outbox fact does not invalidate; rollback does not call afterCommit.
- Book detail invalidation affects only the changed ID; every Book change bumps the page generation, since inserting/deleting/changing metadata can shift any page. Invalidation failure cannot roll back an already-committed DB write. **Residual stale responses are bounded by TTL**, not a linearizable guarantee. If a reader was already started, its response can reflect the previous snapshot.
- Cache hit authorization is enforced by the existing Catalog `SecurityFilterChain` with OAuth2 JWT/scopes *before* entering `BookController`; no JWT, user ID, ISBN or book ID is exported into metric labels. No negative/404 or empty page caching.
- Single-flight coalesces same-key misses *within one JVM* (256 concurrent in-flight keys, 5s follower timeout) to reduce stampede. Across pods, each replica may have one leader; it is deliberately not a distributed mutex.
- Redis errors and corrupt cached JSON fall back to PostgreSQL. Redis is excluded from liveness/readiness. If memory reaches `maxmemory`, new writes can fail without breaking reads, but hit rate degrades; monitor it.
- Pagination is normalized by `PageBook` before lookup and JPA now sorts by `createdAt ASC, id ASC`. Hibernate `@BatchSize(50)` reduces lazy genre N+1, and Flyway V6 provides indexes for visible-book paging and the genre FK. These SQL fixes are independent of Redis.

## Supported configuration and activation

| Setting | Default application property | Local Compose / Kind |
| --- | --- | --- |
| `CATALOG_CACHE_ENABLED` | `false` | `true` |
| `CATALOG_CACHE_REDIS_HOST` | `localhost` | `redis-recommendation-cache` |
| `CATALOG_CACHE_REDIS_PORT` | `6379` | `6379` |
| `CATALOG_CACHE_DETAIL_TTL_SECONDS` | `300` | `300` |
| `CATALOG_CACHE_PAGE_TTL_SECONDS` | `45` | `45` |
| `RECOMMENDATION_CACHE_ENABLED` | `false` | `true` |
| `RECOMMENDATION_CACHE_TTL_SECONDS` | `45` | `45` |

Local domain caches share the **cache-only** Redis named `redis-recommendation-cache` for backward compatibility. The Gateway rate limiter uses a separate Redis service. Namespaces, TTLs and generations remain independently managed. Consider renaming the service when operations standardize a domain-wide cache cluster; no separate Redis per service is needed at this stage.

**Production overlay is intentionally OFF** for both caches. It still contains a placeholder external Redis host and lacks proof of managed endpoint, TLS/ACLs, memory budget, traffic reuse or measured rollback. Turning it ON by changing a YAML boolean would not provision or deploy a production Redis. Do not describe the GitHub merge, Kind tests or this local toggle as production activation. A production rollout must provision an isolated managed Redis endpoint, secrets/ACL, noeviction policy suitable for generation keys, network policy, memory budget and a verified canary rollout.

Rollback: set `CATALOG_CACHE_ENABLED=false` or `RECOMMENDATION_CACHE_ENABLED=false` and redeploy that service. The original JPA/pgvector read path remains functional; no Redis key flush, SQL down migration, JVM process restart beyond redeploy, or schema migration rollback is required. PostgreSQL is the authority.

## Measurements and CI

- `BookReadCacheTest`: bypass, Redis outage, failure propagation.
- `BookReadCacheRedisIT`: real Redis and two independent JVM cache instances, detail/page HIT, invalidation after commit, rollback no-op, stale-fill race and concurrent stampede.
- `CatalogCacheReadBenchmarkIT`: authenticated MockMvc with real PostgreSQL/JPA/Flyway + Redis Testcontainers. Tests detail IDs and stable paginated reads, four modes `cold/warm/mixed80/mixed95`, three client concurrencies and repetitions, latency p50/p95/p99, throughput, PostgreSQL/Hibernate prepared-statement count, HTTP errors; raw JSON `on.json`/`off.json` with same commit, code, seed and runner category.
- [Catalog Redis cache benchmark workflow](../.github/workflows/catalog-cache-benchmark.yml) executes OFF and ON in separate JVM/Maven runs, and uploads JSON/JUnit evidence. It uses `reference` (1,000 requests per scenario x 3 repetitions x concurrency 1/8/32; **72k requests OFF+ON**) for PR #369; `smoke` is 120 requests per scenario / 1 repetition / concurrency 1/8. A manual `stress` profile is 3,000 requests per scenario x 3 reps x concurrency 8/32/64 (216k OFF+ON).
- No threshold based on absolute CI runner latency is used; successful responses and warm-cache zero JPA prepared statements are correctness assertions. **Only report measured results after the workflow succeeds and the raw artifacts are inspected**. An earlier 72k benchmark of Recommendation PR #367 does not measure Catalog performance.

The benchmark drives MockMvc within one process, **not network HTTP throughput**, and cannot establish p95, Redis RTT or memory behavior in a remote production cluster. A real GO for production requires workload-specific hit ratio and access-key distribution, p95/p99 under HTTP/TLS, CPU/GC/pool/memory/evictions, Redis outage/canary recovery and freshness after writes, plus rollback evidence.

## Observability

Micrometer `vellumhub.catalog.cache.events{cache="books",result="hit|miss|bypass|error|corrupt|leader|waiting|joined|capacity|timeout|interrupted|invalidate|invalidate-error"}` and `vellumhub.catalog.cache.serving.duration` are available on the existing Prometheus actuator endpoint. Denominators should exclude bypasses when interpreting hit ratio. No book IDs or customer identifiers in labels. Monitor Redis `used_memory`, noeviction write failures and remaining TTL separately from JVM metrics.

Source files: `module/book/infrastructure/cache/BookReadCache.java`, `module/book/application/event/OutboxBookChangeWriter.java`, `module/book/application/handler/get/GetAllBooksHandler.java`, Flyway `V6__index_visible_book_page_and_genres.sql`.
