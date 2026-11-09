---
name: architecture-fitness-review
description: Use when modifying service boundaries, module dependencies, shared libraries, database ownership or executable architecture rules.
---
# Architecture fitness review

1. Read docs/ARCHITECTURE_FITNESS_FUNCTIONS.md, related ADRs and current checks before proposing new guardrails.
2. Describe each invariant as a falsifiable statement with a concrete prohibited behavior.
3. Check service-to-service build dependencies, domain -> infrastructure coupling, cycles, ownership of data and event contracts.
4. Prefer executable ArchUnit, Enforcer, static checks or integration tests appropriate to the invariant. Add an intentionally violating negative fixture where practical.
5. Distinguish structural checks from behavioral guarantees: a Compose database naming check does not prove production credential isolation; mock events do not prove Kafka delivery.
6. Do not expand exclusions or suppress failures without documenting an explicit reviewed exception.
7. Link rule IDs, source files, validation command and exact commit to evidence. Never claim an invariant covers untested services or all environments.
