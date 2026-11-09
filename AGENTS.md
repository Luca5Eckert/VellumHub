# VellumHub — agent entry point

## Source of truth and scope
Read the issue/PR, repository code and tests, relevant ADR, and existing .github/workflows before editing. GitHub issues are the executable backlog; Obsidian Vault provides explanatory knowledge and is not code or CI proof. Use a feature branch for changes; do not merge, deploy or publish without request.

## Agent skills (load only what the task needs)
- Java/Spring feature: .agents/skills/spring-boot-engineering/SKILL.md
- JPA/SQL/Flyway: .agents/skills/jpa-persistence/SKILL.md
- Java unit/integration/contract tests: .agents/skills/backend-testing/SKILL.md
- OAuth2/JWT/permissions: .agents/skills/backend-security/SKILL.md
- OpenTelemetry/latency: .agents/skills/backend-observability/SKILL.md
- Architecture boundaries: .agents/skills/architecture-fitness/SKILL.md
- Kafka/outbox/schema/evolution: .agents/skills/event-driven-reliability/SKILL.md
- Fault investigation: .agents/skills/systematic-debugging/SKILL.md
- PR self-review: .agents/skills/backend-code-review/SKILL.md
- CI/pipeline security: .agents/skills/ci-hardening/SKILL.md
- JVM performance: .agents/skills/jvm-performance/SKILL.md
- Before delivery: .agents/skills/backend-delivery-verification/SKILL.md

### Focused supplements retained from #355
Use only when the additional specialization is relevant; do not load overlapping skills by default.
- Spring/JPA implementation cross-check: .agents/skills/spring-backend-engineering/SKILL.md
- Authorization-specific threat review: .agents/skills/security-authorization-review/SKILL.md
- Architecture fitness regression review: .agents/skills/architecture-fitness-review/SKILL.md
- Final verification cross-check: .agents/skills/backend-change-verification/SKILL.md

## Validation and quality
Preserve existing Maven profiles, architecture fitness rules and CI gates. Use focused tests during implementation, then the appropriate reactor validation (mvn -B verify); when distributed behavior changes, include relevant Docker-backed / -Pdistributed-e2e checks if the environment supports them. Consult docs/ARCHITECTURE_FITNESS_FUNCTIONS.md and tools/architecture/ for architecture tests. Report skipped/not-run tests rather than claiming green. Confirm CI and acceptance against the exact PR SHA. A green single job, a merged PR and a deployment are distinct evidence stages.

## Project discipline
Use small, coherent commits with behavior and its tests together where appropriate. Avoid unrelated edits. No secrets, credentials, internal employer material or production data in this public repository. A skill does not override an explicit repo rule, issue acceptance, security policy or measured evidence.
