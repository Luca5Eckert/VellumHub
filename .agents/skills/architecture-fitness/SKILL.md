---
name: architecture-fitness
description: Turn VellumHub architectural invariants into executable checks and review changes against them — ArchUnit rules (domain/application/presentation layering, module cycles), Maven Enforcer service isolation, database ownership, Kafka contract fixtures, the AFF fitness catalog and evidence generator, and parity guards for intentionally duplicated code. Use it when a change adds or moves a module, package or shared library, crosses a service boundary, duplicates code between services, or when you find a design rule that only lives in prose and could regress.
---

# Architecture fitness (VellumHub)

A rule that is only written down will drift; a rule with a test that fails on a counter-example will not. The goal is falsifiable, executable invariants with honest scope.

## Procedure
1. Read `docs/ARCHITECTURE_FITNESS_FUNCTIONS.md`, the relevant `docs/architecture` page and the existing checks before inventing a new one. State the invariant as a falsifiable sentence naming the forbidden behaviour ("handlers must not depend on `..presentation..`").
2. Pick the cheapest tool that really enforces it: ArchUnit for packages and cycles, Maven Enforcer for deployable dependencies, Python static checks for repository-wide structure, PostgreSQL/Kafka integration tests for runtime guarantees (roles, ACLs, compatibility).
3. Add a negative fixture that proves the rule fails on a violation (`architecture/fixtures` for ArchUnit, an in-test counter-example for Python). A rule that never failed has not been shown to work.
4. Avoid checks that pass vacuously: use `allowEmptyShould(false)` or explicit assumptions, no broad allow-lists, no baselines that hide new drift. Exceptions need a reviewed, written reason.
5. Keep structural and behavioural claims apart: a Compose naming check is not credential isolation, and a mocked event is not Kafka delivery.
6. When code is duplicated on purpose (for example `OrderedKafkaRecovery` in Engagement and Recommendation, kept per service because images, ACL workflows and evidence are per service), add a parity check so a fix cannot land in only one copy. Extract to `lib/` only together with its Dockerfile stage, image matrix, Compose and workflow path filters.
7. Update the fitness doc or ADR only when the decision itself changed.

## Where things live
- Rule index `tools/architecture/fitness-catalog.json` (AFF-STR-001 … AFF-RES-011) and evidence `tools/architecture/generate_evidence.py`. Its `suites` field matches Surefire report names, so describe Python checks in `baseline`, not `suites`.
- New `tools/architecture/test_*.py` files run in CI automatically through `--run-static` unittest discovery (`.github/workflows/architecture-fitness.yml`).
- ArchUnit suites: `services/*/src/test/java/**/architecture/*ArchitectureFitnessTest`.

## Output
Invariant, enforcing test, negative evidence, CI entry point and explicit scope of what is and is not proven.

Adapted from github/awesome-copilot `java-springboot`; merges the former `architecture-fitness-review` supplement.
