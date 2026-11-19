---
name: spring-boot-engineering
description: Implement or refactor VellumHub Java 21 / Spring Boot 4 backend code — controllers, use-case handlers, application services, DTOs and commands, GlobalExceptionHandler mappings, configuration, transactions and Maven/compiler settings — while keeping the existing layering, error contract and dependency versions. Use it for any change under services/*/src/main/java or a service pom.xml, including small refactors and readability cleanups, even when the user only says "fix", "clean up" or "add an endpoint".
---

# Spring Boot engineering (VellumHub)

The repository, not online examples, defines what "correct" means here: its POMs, package layout, ArchUnit rules and tests. Read them before proposing a pattern.

## Before editing
1. Read `AGENTS.md`, the issue, the target module `pom.xml`, neighbouring classes and their tests.
2. Trace the path end to end: HTTP or Kafka entry → validation → application handler/use case → domain → repository/port → external boundary. Note who owns the data and what happens on failure; most bugs here are at a boundary, not inside a method.

## Conventions that matter
- **Layering.** Use-case handlers (`..application.handler..`) accept application commands/queries (`UpdateBookCommand`, `PageBook`) and return domain or application types (`BookCover`). HTTP DTOs are mapped in the controller or a facade such as `BookService`. Catalog enforces this with ArchUnit; other services should follow it so a later rule does not break them. Domain code never imports infrastructure, adapters or controllers (AFF-STR-001).
- **Error contract.** Each service has `share/exception/GlobalExceptionHandler` with private `respond(...)` helpers. Add a handler by stating status, error label, message and one fixed detail. Public `error`/`message` labels are a client contract: keep them stable even when the class is renamed. Never return SQL, constraint, parser or class names; log a non-sensitive summary instead (constraint name or SQLState, never the database message, which contains the conflicting values). Hidden private resources answer 404 rather than 403 so callers cannot probe for existence.
- **Transactions.** Put the boundary around one business invariant. Avoid network calls inside a database transaction unless the reason is written down, because a slow dependency then holds locks. Multi-service consistency goes through the transactional outbox, not distributed transactions.
- **Build.** Bytecode targets Java 21. Lombok must be declared in `annotationProcessorPaths` (JDK 23+ no longer discovers processors on the classpath). Do not add `--enable-preview`; nothing in the code base needs it and it pins the build to one JDK.
- **Versions and contracts.** Do not change dependency versions, security defaults, serialization or endpoint shapes as a side effect of another change.

## Making the change
- Prefer the smallest behaviourally complete patch. Readability refactors must keep SQL text blocks and error messages byte-identical unless changing them is the point; verify by diffing the extracted strings.
- Add or update tests for success, invalid input, denied access, conflict and downstream failure as they apply (see `backend-testing`).
- Check thread safety, bean lifecycle, timeouts, resource closing, logging context and backward compatibility before declaring it done.

## Hand-offs
`backend-testing` for test selection, `jpa-persistence` for queries and migrations, `backend-security` for auth or data exposure, `event-driven-reliability` when events or projections change, `architecture-fitness` when a package boundary moves, `backend-delivery-verification` before calling it finished.

## Output
What changed and at which boundary, trade-offs, test evidence, and remaining compatibility or operational risks.

Adapted from github/awesome-copilot `java-springboot` (not a verbatim copy); merges the former `spring-backend-engineering` supplement.
