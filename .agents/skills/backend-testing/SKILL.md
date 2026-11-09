---
name: backend-testing
description: Use when changing business behavior, contracts, persistence or asynchronous flows and selecting reliable Java/Spring tests.
---
# Backend testing

1. Start from observable acceptance criteria and existing assertions; preserve regression fixtures.
2. Choose the narrowest meaningful layer: JUnit/Mockito for isolated decisions; Spring MVC/security tests for HTTP contracts; Testcontainers for PostgreSQL, Kafka and transactional semantics; distributed E2E only for cross-service guarantees.
3. For database work, test migrations, constraints, rollbacks, concurrent updates, pagination and duplicate writes where applicable.
4. For events, test duplicate delivery, revision gaps, broker unavailability, retry/replay and contract compatibility. Do not assume an in-memory mock proves Kafka/PostgreSQL atomicity.
5. For security, include unauthenticated, unauthorized and cross-account negative fixtures.
6. Respect pinned libraries and existing Docker requirements; do not adopt Testcontainers 2 examples verbatim if the module uses 1.x.
7. Run focused tests, then the existing repository validation. Report skips, Docker limitations, execution command and exact SHA.

Never weaken coverage thresholds or suppress a failing test to get green CI.
