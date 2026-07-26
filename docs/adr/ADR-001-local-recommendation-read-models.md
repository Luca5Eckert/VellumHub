# ADR-001 - Local Recommendation Read Models

- Status: Accepted
- Scope: Recommendation serving and upstream state propagation
- Decision owner: Lucas Eckert
- Last reviewed: 2026-07-26

## Context

The recommendation service needs catalog metadata, user preference state, ratings, reactions, and reading progress to produce personalized results.

A request-time design could call catalog, user, and engagement services synchronously. That would make recommendation latency and availability depend on every upstream service and would turn partial failures into user-facing recommendation failures.

An earlier architecture also used a Python ML sidecar for embedding and retrieval work, adding another runtime, deployment unit, and network boundary.

## Decision

Use Event-Carried State Transfer to maintain recommendation-owned read models:

1. source services own business writes and their databases;
2. source services publish business events through Kafka;
3. recommendation consumers update local projection tables;
4. recommendation serving reads only from its own PostgreSQL/pgvector database;
5. embeddings are generated in-process on the JVM;
6. HNSW cosine search retrieves vector candidates locally;
7. retry topics and Dead Letter Topics expose asynchronous processing failures.

The recommendation hot path must not synchronously call catalog, user, or engagement services.

## Local state

The recommendation service owns derived tables for:

- book embeddings and popularity signals;
- user profile vectors and interaction history;
- denormalized book metadata required to build responses.

These tables are projections, not competing sources of truth. Source services remain authoritative for their business domains.

## Alternatives considered

### Synchronous upstream composition

Advantages:

- latest source state at request time;
- fewer local projections;
- simpler write-side event model.

Rejected because one slow or unavailable upstream service would degrade the full recommendation request. It also creates request-time fan-out and tighter release coupling.

### Shared database tables

Advantages:

- direct joins;
- no event propagation delay;
- fewer duplicated fields.

Rejected because shared application tables hide ownership and couple service deployments and schema evolution.

### External Python embedding sidecar

Advantages:

- broad Python ML ecosystem;
- independent model deployment.

Replaced in the current path because it added a network hop and another runtime boundary for a model available in-process on the JVM.

### Specialized vector database

Advantages:

- broader vector-native operational and indexing features.

Not selected for the current system because PostgreSQL/pgvector keeps vectors close to relational metadata, local transactions, and the existing Docker workflow.

## Consequences

### Positive

- Recommendation serving has zero synchronous upstream fan-out.
- Partial upstream outages do not automatically make local recommendation reads unavailable.
- Data ownership remains explicit.
- The Python sidecar and network hop are removed from the serving path.
- Kafka failure paths are inspectable through retry and DLT behavior.
- Local read models can be indexed and shaped for recommendation queries.

### Negative

- Recommendation state is eventually consistent.
- Event contracts become critical compatibility surfaces.
- Consumers need idempotency, replay, and recovery strategies.
- Projection schemas duplicate selected upstream fields.
- Transactional outbox behavior is required to close write/event consistency gaps.
- Local embedding inference consumes JVM memory and CPU.

## Evidence

Current project evidence:

- five application services;
- zero synchronous calls to catalog, user, or engagement in the recommendation hot path;
- latest consolidated validation: 478 Maven tests passing;
- local benchmark notes: approximately 300-500 ms with the previous Python sidecar and 80-120 ms with in-process JVM embeddings and pgvector.

The latency values are local project benchmarks, not production SLAs.

## Operational requirements

This decision requires:

- centralized topic contracts;
- idempotent consumers;
- transactional event publication;
- retry and DLT monitoring;
- consumer lag and projection health metrics;
- integration tests using real Kafka and PostgreSQL behavior;
- replay and repair procedures for stale projections.

## Current gaps

- The benchmark needs a reproducible public harness with hardware, dataset, warm-up, and percentile reporting.
- Consumer idempotency and transactional outbox remain active hardening work.
- Not every distributed failure path is covered by Testcontainers.
- Projection freshness is not yet exposed as a user-facing SLO.

## Related documentation

- [VellumHub README](../../README.md)
- [Observability](../OBSERVABILITY.md)
- [Observability runbooks](../OBSERVABILITY_RUNBOOKS.md)
