---
name: backend-delivery-verification
description: Decide whether a VellumHub backend change is really done — map every acceptance criterion to code and evidence, run focused tests plus the reactor verify (Docker-backed suites included when available), check architecture and documentation guards, and compare CI against the exact PR head SHA. Use it before saying work is complete, before opening or approving a PR, after fixing CI, and whenever someone asks "is this ready?", "can we merge?" or "did everything pass?".
---

# Backend delivery verification (VellumHub)

"Implemented", "tests pass locally", "CI green on this SHA", "merged", "deployed" and "operationally validated" are different claims. Reporting the strongest one you actually have — and naming the gaps — is what makes the summary trustworthy.

## Procedure
1. Extract each acceptance criterion from the issue, including security, migration, failure and non-functional ones.
2. Build a matrix: criterion → changed code → test or check → result. A criterion without its own evidence is `unverified`, not `passed`.
3. Run focused tests first, then the repository validation:
   - `./mvnw -B verify` for the reactor. Default runs include Testcontainers suites (`*DistributedTest`, `*PostgresIntegrationTest`, `*PostgresTest`, `*IT`); without Docker they error, which is an environment gap, not a pass or a product failure.
   - `python -m unittest discover -s tools/architecture -p "test_*.py"` and `python tools/architecture/validate_docs.py` when architecture or docs changed.
   - `-Pdistributed-e2e` when cross-service behaviour changed and the environment supports it.
   - CI uses JDK 21; a newer local JDK is fine only with the compiler settings described in `spring-boot-engineering`.
4. Read the output, not just the exit code: skipped tests, tests that ran zero cases, ignored suites, generated files and unrelated diffs.
5. Load other skills selectively (security, events, architecture) for the criteria that need them; do not activate every skill by default.
6. Check remote CI against the PR head SHA. A green run on an earlier commit, or one job of several, does not cover the final diff.
7. Review the final diff for secrets, new dependencies, weakened guards and stale documentation. Do not merge, publish or deploy without an explicit request.

## Output
Criterion → evidence → status matrix (`passed`, `failed`, `skipped`, `blocked`, `unverified`), commands with environment and SHA, blockers with the smallest reproduction, and a merge recommendation that states its limits.

Adapted from obra/superpowers `verification-before-completion`; merges the former `backend-change-verification` supplement.
