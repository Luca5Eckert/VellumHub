# Agent instructions — VellumHub

Read README.md and the issue/PR acceptance criteria before editing. Inspect the current module, dependency versions, configuration, tests and relevant ADRs; source and runnable tests take precedence over narrative docs.

## Skill routing (read only what matches the task)
- Spring/JPA/API behavior: .agents/skills/spring-backend-engineering/SKILL.md
- Unit, integration, contract or distributed tests: .agents/skills/backend-testing/SKILL.md
- Errors, regressions or red CI: .agents/skills/systematic-debugging/SKILL.md
- Identity, token handling or authorization: .agents/skills/security-authorization-review/SKILL.md
- Kafka, Outbox, schema evolution or projection consistency: .agents/skills/event-driven-reliability/SKILL.md
- Module boundaries or architecture fitness functions: .agents/skills/architecture-fitness-review/SKILL.md
- Before requesting merge / declaring an issue done: .agents/skills/backend-change-verification/SKILL.md

## Repository invariants
- Never edit directly on main for an issue; use a scoped branch. Keep commits small, semantic and focused. Preserve uncommitted user changes; do not alter unrelated modules.
- Respect Java and Spring versions declared by each module POM. Do not upgrade dependencies or alter quality gates merely to satisfy a skill.
- Service data ownership and integration contracts are architectural constraints. Do not substitute synchronous service fan-out for local projections without an explicit decision.
- The existing architecture fitness, Maven, contract, Postman and distributed workflows are authoritative for their respective coverage. See docs/ARCHITECTURE_FITNESS_FUNCTIONS.md and .github/workflows/.
- Run focused tests first. For the Java reactor use mvn -B verify when available; the distributed reactor is opt-in via mvn -B -Pdistributed-e2e verify and requires Docker. Verify which tests actually ran.
- A passing local command is not evidence that remote CI passed; CI passing is not evidence of deployment. Always record exact commit SHA, checks, skips and remaining acceptance gaps.
- Reconcile durable decisions with appropriate ADRs and Obsidian project references only when scope and verification justify it; never copy private employer data here.
- No automatic PR merge, issue closure or deployment without explicit instruction.
