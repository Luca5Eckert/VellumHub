---
name: jpa-persistence
description: Review or change VellumHub persistence — JPA entities and mappings, Spring Data repositories and Specifications, JdbcTemplate SQL (outbox claims, checkpoints, quarantine), Flyway migrations, locking and transaction semantics on PostgreSQL. Use it whenever a change touches an @Entity, a repository or Specification, a SQL text block, a db/migration file, pagination or visibility filters, or when a query is slow, duplicated (N+1) or racing.
---

# JPA and persistence (VellumHub)

Each service owns its PostgreSQL database and must never read another service's schema (AFF-DATA-004). Correctness usually depends on what the database enforces, so test with real PostgreSQL whenever constraints, isolation, locking or dialect matter.

## Procedure
1. Inspect tables, constraints, indexes, Flyway history, mappings and real callers. Locate the transaction boundary and where authorization is applied.
2. Apply ownership and visibility in the query before pagination (owner, membership, PUBLIC/PRIVATE as in `BookListSpecificationProvider`); filtering after loading leaks counts and wastes work. There is no tenant model.
3. Look for N+1 loads, missing projections, eager cascades, large entity graphs, implicit flushes, long transactions and unbounded queries.
4. Choose concurrency control from the invariant: optimistic `@Version`, unique constraints, `SELECT … FOR UPDATE`, `SKIP LOCKED` claims or idempotency keys. The outbox and checkpoint code relies on these; keep their SQL text identical during refactors unless the change is intended and tested.
5. Migrations must work during rolling deploys: expand, backfill, then contract. Flyway history is append-only; state the rollback or forward-fix plan.
6. Make performance claims only with the same dataset, plan, warm-up and load (`EXPLAIN ANALYZE` where useful).
7. Never change production schema, repair data or touch persistent volumes without explicit authorization.

## Output
Invariant, query or migration change, compatibility analysis, PostgreSQL test evidence, and anything left unverified.

Adapted from github/awesome-copilot `java-hibernate-jpa` and `postgresql-code-review`.
