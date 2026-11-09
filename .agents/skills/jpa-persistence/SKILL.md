---
name: jpa-persistence
description: Review Spring Data JPA, Hibernate, PostgreSQL migrations and transactional integrity without changing repository policy implicitly.
---

# jpa-persistence

## Trigger
Entity models, repositories, SQL, Flyway, transaction anomalies, query latency, locking or schema evolution.

## Procedure
1. Inspect tables, foreign keys, indexes, Flyway history, JPA mappings and real callers; locate transaction and authorization boundaries.
2. Verify that query filters enforce tenant/organization scope before pagination, not after materialization.
3. Look for N+1, missing projections, eager-loading cascades, excessive entity graphs, implicit flushes, long transactions and unbounded queries.
4. For concurrency, choose between optimistic locking, unique constraints, database locks and idempotency based on invariant and measured contention.
5. Make migrations backward-compatible with rolling deployment where needed. Distinguish schema expand/backfill/contract and define rollback or forward-fix expectations.
6. Test with real PostgreSQL where SQL dialect, constraints, isolation or locking matters. Assert error conditions, not only happy-path persistence.
7. Benchmark with same dataset, plan, warm-up and load before making performance claims. Record EXPLAIN/ANALYZE cautiously when supported.
8. Never change production schema, repair data or migrate persistent volumes without explicit authorization.

## Output
Invariant, query/migration change, compatibility analysis, test fixture and measured evidence or unverified status.

## Reference
Adapted from https://github.com/github/awesome-copilot/tree/main/skills/java-hibernate-jpa/SKILL.md and https://github.com/github/awesome-copilot/tree/main/skills/postgresql-code-review/SKILL.md.
