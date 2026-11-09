---
name: backend-change-verification
description: Use before final delivery, PR review or merge readiness to reconcile acceptance, implementation, tests, architecture and CI.
---
# Backend change verification

1. Read the issue acceptance criteria and target branch diff; map every criterion to changed code and a concrete test or other proof.
2. Inspect negative cases: invalid input, authorization, concurrency, timeout, retry, duplicates, schema compatibility and rollback where relevant.
3. Apply spring-backend-engineering, event-driven-reliability, security-authorization-review or architecture-fitness-review selectively; do not activate all skills by default.
4. Execute focused checks plus existing CI-equivalent commands. For VellumHub consult docs/ARCHITECTURE_FITNESS_FUNCTIONS.md and current workflows; do not invent a single universal green command.
5. Inspect errors, ignored or skipped suites, generated artifacts, unrelated file changes and any weakened guardrails.
6. Check remote CI against the exact head SHA of the proposed PR, not an earlier successful run.
7. Produce a criterion -> evidence -> status matrix and classify each result as passed, failed, skipped, blocked or unverified.
8. Explicitly state remaining risks and whether the PR is ready for human review; do not merge or claim deployment automatically.
