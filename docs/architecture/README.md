---
id: vh-architecture
slug: architecture
type: architecture-index
system: vellumhub
status: implemented-in-repository
c4_levels: [context, container, component]
related:
  - vh-services
  - vh-c4-context
  - vh-c4-containers
  - vh-c4-catalog-components
  - vh-outbox-delivery
  - vh-cache-rollout
  - vh-api-request-authorization
  - vh-google-federation
  - vh-auth-identity-lifecycle
  - vh-ordered-consumer
  - vh-kafka-quarantine
  - vh-event-wire-format
---

# VellumHub — architecture at a glance

**Purpose:** a compact, code-backed view of the architecture implemented in this repository. Source code, API/Kafka contracts, migrations and tests are authoritative for runtime behavior; these Markdown diagrams are explanatory snapshots, not evidence of deployment.

**Implemented shape:** six Java deployables (Auth, Gateway, User, Catalog, Engagement, Recommendation); five service-owned PostgreSQL databases; Kafka integration facts; a dedicated Gateway Redis limiter and optional domain response-cache Redis. Auth issues access tokens; Gateway routes APIs; Recommendation reads local projections.

| View | Reference | What it shows |
| --- | --- | --- |
| System boundary | [C4 L1](diagrams/c4-context.md) | Users and external dependencies |
| Service ownership | [C4 L2](diagrams/c4-containers.md) · [service registry](services/README.md) | Deployables, data and messages |
| Catalog internals | [C4 L3](diagrams/c4-catalog-components.md) | Read cache, writes and Outbox |
| Registration | [Auth → User](flows/identity-onboarding.md) · [Identity states](states/auth-identity-lifecycle.md) | Identity and idempotent provisioning |
| API security | [Request authorization](flows/api-request-authorization.md) · [Google federation](flows/google-federation.md) | JWT, scopes, ownership, rate limits; Google login and link |
| Event delivery | [Book → Kafka](flows/book-outbox.md) · [Outbox states](states/outbox-delivery.md) | Local commit and delayed delivery |
| Read models | [Feedback → ranking](flows/recommendation-feedback.md) | Autonomous Recommendation reads |
| Ordered consumers | [Checkpoint and retry](flows/ordered-consumer.md) · [Quarantine states](states/kafka-quarantine.md) | Revision order, duplicates, DLT and replay |
| Wire format | [JSON → Avro states](states/event-wire-format.md) | Per-stream cutover and rollback |
| Cache | [Cache-aside](flows/catalog-cache.md) · [rollout states](states/cache-rollout.md) | Optional reads and staged invalidation |
| Browse | [HTML explorer](architecture-explorer.html) | Diagrams rendered from these Markdown files |

**Operational distinction:** Compose/Kind locally enable domain caches; application defaults and the unprovisioned production overlay remain OFF. GitHub Actions and synthetic benchmarks validate only their stated environments. See [Catalog operations](../CATALOG_REDIS_CACHE.md), [Recommendation cache](../RECOMMENDATION_REDIS_CACHE.md) and [deployment](../../deploy/kubernetes/README.md).

Service identifiers (`VH-AUT`, `VH-GTW`, `VH-USR`, `VH-CAT`, `VH-ENG`, `VH-REC`) are stable navigational labels. Use [documentation conventions](CONTRIBUTING.md) when changing topology. This repository presents the **current implementation and results**; detailed decision history, alternatives, plans and dated experiments are maintained in the engineering Vault.
