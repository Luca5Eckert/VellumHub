---
name: spring-backend-engineering
description: Use for Java 21 and Spring Boot API, domain, JPA or service changes in VellumHub while preserving existing boundaries and dependency versions.
---
# Spring backend engineering

1. Read the target module POM, neighboring implementation and tests; check the actual Spring Boot version and existing conventions before proposing changes.
2. Trace HTTP contract -> validation -> application use case -> domain rules -> persistence/adapter; keep mappings and persistence entities outside public API contracts.
3. Put transaction boundaries at the business operation, verify rollback semantics and avoid external network calls inside a database transaction without justification.
4. Check JPA N+1, fetch scope, pagination, unique constraints, optimistic concurrency and migration compatibility when changing data access.
5. Prefer the smallest coherent change; preserve error mapping, authentication and observability behavior.
6. Add behavioral tests for success, invalid input, forbidden scope, conflicts and downstream failures relevant to the change.
7. Do not introduce a new architectural style or dependency upgrade solely because an example recommends it.

Hand off to backend-testing and backend-change-verification. Use event-driven-reliability when events or projections are affected.
