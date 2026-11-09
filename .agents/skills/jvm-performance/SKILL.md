---
name: jvm-performance
description: Investigate Java performance with reproducible measurements, profiler evidence and JVM-aware hypotheses.
---

# jvm-performance

## Trigger
Latency, throughput, memory, allocation, GC, thread contention or JVM regressions.

## Procedure
1. Define target endpoint/job, workload, dataset, platform, warm-up, concurrency and statistical baseline before proposing tuning.
2. Differentiate application, database, connection pool, network, GC, startup and external dependency time.
3. Use available evidence such as JFR, thread/heap analysis, GC logs, p95 traces, SQL plans or flamegraphs. Never infer a culprit from one high latency value.
4. Test the smallest change under comparable load; report distribution, units and confidence/limitations.
5. Guard against benchmark contamination (cold starts, cache state, unrealistic data, missing backpressure).
6. Validate correctness and resource pressure as well as speed; do not trade consistency/security for a faster number without decision.
7. Do not claim production improvements from local or historical benchmarks.

## Output
Hypothesis, baseline protocol, test evidence, outcome and follow-up observability.

## Reference
Adapted from https://github.com/github/awesome-copilot/tree/main/skills/java-diagnostics/SKILL.md.
