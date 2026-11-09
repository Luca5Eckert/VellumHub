---
name: spring-boot-engineering
description: Implement or refactor Java and Spring Boot backend features using the current repository architecture and supported dependencies.
---

# spring-boot-engineering

## Trigger
Java/Spring controllers, services, configuration, transactions, DTOs, integrations, error handling or application design.

## Procedure
1. Read AGENTS.md, the active issue, Maven POM, module conventions and focused tests. Treat repository dependencies, not online examples, as authoritative.
2. Map the end-to-end path: HTTP/event entry point -> validation -> use case/domain -> repository/port -> external boundary. Confirm ownership and failure semantics before editing.
3. Keep DTOs and domain models distinct at boundaries; do not leak persistence entities or secrets in responses. Use explicit validation and stable, documented error contracts.
4. Define transaction boundaries around a coherent business invariant. Avoid distributed transactions by default; articulate alternatives for multi-service changes.
5. Respect Spring Boot 4 / Java 21 conventions actually in use. Never silently change dependency versions, security defaults, serialization or endpoint contracts.
6. Introduce the smallest behaviorally complete patch, with tests for positive, denied, invalid and retry/failure paths as appropriate.
7. Inspect thread safety, bean lifecycle, timeouts, resource closure, logging context and backward compatibility.
8. Handoff to backend-testing, backend-security, architecture-fitness and backend-delivery-verification when relevant.

## Output
Changed behavior and boundaries; trade-offs; test evidence; known compatibility or operational risks.

## Reference
Adapted from https://github.com/github/awesome-copilot/tree/main/skills/java-springboot/SKILL.md (not a verbatim copy).
