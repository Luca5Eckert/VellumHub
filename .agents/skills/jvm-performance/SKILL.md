---
name: jvm-performance
description: Investigate VellumHub Java performance with reproducible measurements — endpoint or consumer latency, throughput, memory and allocation, GC pauses, thread or connection-pool contention, embedding and recommendation ranking cost, cache effectiveness — using JFR, profiles, SQL plans and the existing benchmark harnesses. Use it when something is "slow", a benchmark regressed, a p95/p99 target is discussed, or a tuning change (JVM flags, pool sizes, cache settings) is proposed.
---

# JVM performance

One slow number is an anecdote. Decisions need a baseline, a controlled comparison and a stated environment; otherwise tuning moves the problem instead of fixing it.

## Procedure
1. Define the target (endpoint, consumer or job), workload, dataset, platform, warm-up, concurrency and baseline before proposing a change. Reuse existing harnesses such as `services/recommendation-service/benchmark` and the catalog cache benchmark workflow.
2. Separate application, database, connection pool, network, GC, startup and external dependency time.
3. Use evidence — JFR, thread and heap dumps, GC logs, traces, `EXPLAIN ANALYZE`, flame graphs — before naming a culprit.
4. Change one thing, measure under comparable load, and report distributions with units and limitations.
5. Guard against contamination: cold starts, cache state (Redis caches are optional and OFF by default outside Compose/Kind), unrealistic data, missing back-pressure.
6. Check correctness and resource pressure too; do not trade consistency or security for speed without an explicit decision.
7. Hosted-runner and local numbers are references, not production claims.

## Output
Hypothesis, baseline protocol, evidence, outcome and the observability needed to keep watching it.

Adapted from github/awesome-copilot `java-diagnostics`.
