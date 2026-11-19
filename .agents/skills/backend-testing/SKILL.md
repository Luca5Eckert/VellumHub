---
name: backend-testing
description: Choose, write and run meaningful VellumHub Java tests — JUnit 5, Mockito, Spring MVC slices, ArchUnit and Testcontainers 1.21.4 with real PostgreSQL, Kafka and Schema Registry — and report honestly what ran. Use it for any behaviour change, bug reproduction, refactor that needs a safety net, flaky or failing test, or when deciding which Maven command proves a change, including requests like "add tests", "is this covered?" or "why does mvn test fail locally?".
---

# Backend testing (VellumHub)

A test is evidence only if it asserts an observable outcome at the boundary where the behaviour lives, and only if it actually ran. Mocks prove wiring, not delivery.

## Pick the boundary
- **Domain rules:** fast unit tests over input partitions and edges (for example the `BookList` permission matrix).
- **HTTP and security:** MVC slice or web integration for status codes, validation, anonymous/denied/foreign-resource cases and the error body (`GlobalExceptionHandler*Test`, including the leak tests).
- **Persistence:** real PostgreSQL for constraints, locking, transactions, migrations and SQL dialect.
- **Messaging:** broker-backed tests for duplicates, malformed payloads, retry/DLT and ordering (`ConsumerIdempotencyDistributedTest`, outbox `*DistributedTest`).
- **Cross-service:** `-Pdistributed-e2e`, only for critical paths.

## Discipline
1. Read existing test utilities and versions first. Testcontainers is pinned at 1.21.4 (`testcontainers.version`); its 2.x APIs differ.
2. When fixing a bug, write the failing test first when practical. For refactors, add characterization tests for the paths you restructure (for example `case … when` branches) so the refactor is observable.
3. Keep clocks, randomness, retries and fixtures deterministic.
4. Run focused tests, then `./mvnw -B verify`. Default runs include Docker-backed suites (`*DistributedTest`, `*PostgresIntegrationTest`, `*PostgresTest`, `*IT`); without Docker they **error** (`Could not find a valid Docker environment`) instead of skipping. Report them as an environment gap, separately from product failures.
5. Python structural checks: `python -m unittest discover -s tools/architecture -p "test_*.py"`.
6. Coverage is a diagnostic, not proof. Keep existing JaCoCo floors and architecture guards rather than inventing a percentage.

## Output
Commands, SHA, tests run/failed/skipped/errored per module, negative cases covered and those left uncovered.

Adapted from github/awesome-copilot `spring-boot-testing`, `java-junit` and `java-testcontainers`.
