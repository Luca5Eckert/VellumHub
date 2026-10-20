# Architecture Fitness Functions — implementation guide

> **Status: proposed, 2026-10-20.** This document describes planned architecture verification. It does not mean new gates are enabled or that production guarantees have been verified. The [Obsidian source of truth](https://github.com/Luca5Eckert/obsidian-vault/blob/main/projects/vellum-hub/architecture/Architecture%20Fitness%20Functions%20-%20Fonte%20da%20Verdade.md) and [ADR-018](https://github.com/Luca5Eckert/obsidian-vault/blob/main/projects/vellum-hub/architecture/adr/ADR-018-architecture-fitness-functions.md) define the normative goals, rationale and trade-offs.

## Purpose and existing evidence

Fitness functions turn architectural decisions into repeatable checks. They complement domain tests: a feature can work while introducing a dependency cycle, runtime HTTP fan-out, unowned data access or unsafe event processing.

VellumHub already has useful fitness-function evidence:
- [Distributed Testing](DISTRIBUTED_TESTING.md): real Kafka, PostgreSQL/pgvector, Flyway, consumers and bounded failure tests via Testcontainers.
- [Recommendation benchmark](../services/recommendation-service/benchmark/README.md): event-to-projection, authenticated event-to-visible ranking and reads from materialized local state with User/Catalog/Engagement unavailable, smoke/reference modes and machine-readable results.
- Catalog/Engagement transactional Outbox and transactional consumer checkpoints on selected canonical streams. Merge and test evidence do **not** imply completed production rollout or globally exactly-once delivery.
- The root Maven reactor contains five deployable services, `lib/kafka-contracts`, `lib/distributed-test-support`, and an opt-in cross-service `integration-tests/distributed-e2e` module.

Do not recreate the #283 benchmark or independently reimplement features covered by #200, #201 or #202.

## Proposed catalog

| ID | Contract | Verification |
| --- | --- | --- |
| AFF-STR-001 | No forbidden domain-to-infrastructure/application-to-adapter dependencies inside a service; document justified JPA exceptions | ArchUnit in each affected service |
| AFF-STR-002 | No prohibited cross-module dependency cycles | ArchUnit slices, aligned with real packages |
| AFF-BOUND-003 | Deployable services do not depend on other services' implementation artifacts | Maven graph/build check; allow shared contracts and opt-in E2E |
| AFF-DATA-004 | No cross-domain database ownership violations in observed paths | Config/SQL inspection and integration checks |
| AFF-EVT-005 | Business mutation and Outbox intent commit or roll back atomically | Real PostgreSQL + scoped failure injection |
| AFF-EVT-006 | Duplicate delivery or crash does not double-apply effects, and checkpoint/projection commit together | Real Kafka/PostgreSQL integration |
| AFF-EVT-007 | Per-aggregate ordering/version-gap policy is enforced | Real stream consumer tests |
| AFF-CON-008 | Supported producer/consumer payload versions remain compatible | Kafka contract fixtures and serialisation tests |
| AFF-REC-009 | Already-materialized recommendation reads work without upstream synchronous calls | Existing #283 distributed harness, augmented only for gaps |
| AFF-OPS-010 | Freshness, lag, catch-up and success rates are reproducibly measured | Existing benchmark reference artifacts |
| AFF-RES-011 | Retry, quarantine, DLT and replay follow each **implemented** stream policy | Failure/recovery scenarios; coordinate #272 |
| AFF-SEC-012 | Token verification remains local, without issuer call per request or private signing key in validators | Config and integration; coordinate #163/#304/#305 |

**First wave:** STR-001, STR-002, BOUND-003, EVT-005, EVT-006 and REC-009. All new IDs remain *proposed* until an implementing PR proves they are enforced or monitored.

## Planned implementation boundaries

- **Static:** JUnit + ArchUnit tests under each service's `src/test/java/.../architecture/`. Keep a small number of specific rules. Check actual service package structures first; some domain types intentionally use JPA. Never introduce arbitrary blanket package restrictions.
- **Build:** inspect service-to-service production dependency edges in the Maven reactor; explicitly exclude the cross-service test-only module and permit `kafka-contracts` as shared contracts.
- **Behavioral:** extend existing JUnit/Testcontainers suites at the actual transactional boundary, including PostgreSQL semantics, Kafka delivery and Flyway migrations. Assert rollback on Outbox failure, recovery after broker outage, redelivery idempotence, checkpoint atomicity and stream-specific revision gaps.
- **Runtime:** materialize through events, isolate upstream services, issue authenticated `GET /recommendations` queries and assert zero upstream HTTP calls during the read. Distinguish read autonomy from availability of *new* producer events.
- **Measurements:** retain the benchmark's event-to-visible (not merely event-to-row) boundary, per-run raw evidence, SHA, scenario and hardware metadata. No unconditional p95 threshold in shared CI.

## Baseline, CI and governance

1. Audit current packages, POM dependencies and test coverage before enforcing rules. Record existing violations with exact rule, scope, rationale, owner and planned review. Fail **new** violations; test the rules themselves with intentional negative fixtures where appropriate.
2. Static fitness functions run in regular PR verification without Docker. Behavior-specific checks run for affected flows with real Kafka/PostgreSQL; distributed smoke verifies deterministic architectural invariants.
3. Keep reference performance and catch-up benchmarks out of brittle CI pass/fail thresholds. Treat them as reproducible trending evidence, not production SLAs.
4. Preserve existing distributed-test timing bounds, test isolation and commands. Avoid fixed sleeps, broad retries that hide defects and a custom testing framework.
5. Each implemented AFF needs stable ID, linked ADR, owner, code location, verification command, baseline, status, CI evidence and known limits. Change the ADR/spec when consciously changing an invariant.
6. Every implementing PR updates the affected READMEs, distributed testing docs and Obsidian, with CI artifacts/merge SHA. Do not mark an issue as implemented on creation or after a draft PR alone.

Existing commands (these are **not** new AFF gates):

~~~bash
mvn -B -ntp -DexcludedGroups=distributed clean verify
mvn -Pdistributed-e2e -pl integration-tests/distributed-e2e -am -Dgroups=distributed test
~~~

See [Recommendation benchmark](../services/recommendation-service/benchmark/README.md) for its intentionally separate per-suite invocation. New commands will be documented when their tests are actually added and run.

## Scope boundaries

This initiative does not implement OAuth2/OIDC, #272 replay functionality, full hexagonal restructuring, a new observability platform, universal exactly-once, a production SLO or a generic fitness-function framework. It codifies decisions already made and makes architectural drift visible with the cheapest appropriate verification boundary.
