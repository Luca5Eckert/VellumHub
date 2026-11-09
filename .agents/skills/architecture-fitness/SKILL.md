---
name: architecture-fitness
description: Turn important backend architectural invariants into enforceable tests instead of unenforced prose.
---

# architecture-fitness

## Trigger
New service/module, dependency direction, persistence ownership, message contract, security boundary or refactor spanning packages.

## Procedure
1. Read existing ADRs and executable guardrails; identify the actual invariant and who owns it.
2. Prefer an executable, stable assertion: ArchUnit for package/module boundaries and cycles; Maven Enforcer for deployable dependencies; integration tests for authorization, SQL roles, brokers and contract compatibility.
3. Write a negative fixture that demonstrates the guard fails for a forbidden dependency, cycle or unauthorized access when possible.
4. Avoid tests that pass by skipping all discovered classes, broad allow-lists or snapshot baselines that hide drift.
5. Check that the rule is useful across expected evolutions, has clear actionable failure text and runs in existing CI.
6. Distinguish static config checks, container-level guarantees and real operational isolation; do not claim one proves the other.
7. Update the relevant ADR or canonical architecture note only if the engineering decision changed.

## Output
Invariant, implementation test, negative evidence and scope of proof.

## Reference
Adapted from https://github.com/github/awesome-copilot/tree/main/skills/java-springboot/SKILL.md; tailored to project architecture-fitness practices.
