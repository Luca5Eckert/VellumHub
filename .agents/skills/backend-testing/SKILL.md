---
name: backend-testing
description: Select meaningful Java/Spring tests with JUnit, Mockito and Testcontainers, aligned to the repository's existing validation commands.
---

# backend-testing

## Trigger
Any backend behavior change, bug reproduction, contract change or reliability/quality regression.

## Test selection
- Pure domain logic: fast unit tests for invariant, input partition and boundary values.
- HTTP/security: Spring MVC slice or actual web integration for status, DTO validation, anonymous/denied cases and security filters.
- Persistence: PostgreSQL integration for constraints, transactions, locking, migration and SQL semantics.
- Messaging: broker-backed integration for duplicate delivery, malformed payload, retry, DLT and ordering assumptions.
- Multi-service behavior: explicit distributed E2E only for critical boundaries. Do not mistake mocks for end-to-end proof.

## Execution discipline
1. Read existing test libraries and versions. Both personal projects currently pin Testcontainers 1.21.4; do not copy Testcontainers 2.x APIs blindly.
2. Create a regression test that fails before the fix when practical. Keep clocks, randomness, retries and fixture datasets deterministic.
3. Run focused tests, then the project's canonical verification; for Docker-dependent suites, report unavailable/skipped tests as gaps.
4. Coverage is diagnostic, not proof of behavior. Preserve repository-specific JaCoCo minimums and architecture guards instead of imposing a generic percentage.
5. Report exact commands, commit SHA, tests run/skipped and negative cases not covered.

## Done
Expected behavior is asserted at the correct boundary, with reproducible pass/fail evidence.

## Reference
Adapted from https://github.com/github/awesome-copilot/tree/main/skills/spring-boot-testing/SKILL.md, https://github.com/github/awesome-copilot/tree/main/skills/java-junit/SKILL.md and https://github.com/github/awesome-copilot/tree/main/skills/java-testcontainers/SKILL.md.
