---
name: backend-code-review
description: Perform evidence-backed backend PR reviews for correctness, security, failure semantics, architecture and tests.
---

# backend-code-review

## Trigger
Self-review before opening a PR, requested code review, review feedback or merge readiness check.

## Procedure
1. Read issue acceptance and repository rules. Review the diff against its parent, not just the changed file names.
2. Follow changed behavior end-to-end (handler -> authorization -> domain -> storage/event -> response).
3. Examine negative and adversarial cases: invalid input, unauthorized access, concurrent requests, partial failure, retry, out-of-order events and compatibility.
4. Inspect migration/contract risks, secrets, error logging, resources, timeouts, and existing architecture fitness rules.
5. Separate blocking correctness/security findings from maintainability suggestions and style. Provide file/line location and concrete failure scenario.
6. Review tests as proofs: assert observable outcome, use correct environment and check that they actually ran.
7. After fixes, re-review final diff and exact SHA; do not reuse stale CI results to declare merge-ready.

## Output
Findings ordered by severity, evidence, minimal corrections and explicitly unverified paths.

## Reference
Adapted from https://github.com/obra/superpowers/tree/main/skills/requesting-code-review/SKILL.md and https://github.com/obra/superpowers/tree/main/skills/receiving-code-review/SKILL.md.
